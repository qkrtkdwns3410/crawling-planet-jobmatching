#!/bin/bash
set -euxo pipefail

# ============================================
# Crawling Planet - EC2 Bootstrap Script
# Amazon Linux 2023 / Java 21 / Chrome / PostgreSQL 16
# 2개 앱: module-app(8080) + module-api(8081)
# ============================================

# --- 시스템 업데이트 ---
dnf update -y
dnf install -y --allowerasing curl jq nginx

# --- Swap (OOM 안전망, 1GB) ---
if [ ! -f /swapfile ]; then
  fallocate -l 1G /swapfile
  chmod 600 /swapfile
  mkswap /swapfile
  swapon /swapfile
  echo '/swapfile none swap sw 0 0' >> /etc/fstab
fi
echo 'vm.swappiness=10' >> /etc/sysctl.conf
sysctl -p

# --- Java 21 (Amazon Corretto) ---
dnf install -y java-21-amazon-corretto-devel

# --- EBS 데이터 볼륨 마운트 (PostgreSQL 데이터 영구보존) ---
# Terraform aws_volume_attachment 리소스가 볼륨을 선언적으로 attach함
# user_data는 디바이스가 나타날 때까지 대기만 담당
# 디바이스 나타날 때까지 대기 (NVMe 기반 인스턴스는 /dev/nvme1n1)
DATA_DEVICE=""
for i in $(seq 1 30); do
  if [ -b /dev/nvme1n1 ]; then
    DATA_DEVICE=/dev/nvme1n1
    break
  elif [ -b /dev/xvdb ]; then
    DATA_DEVICE=/dev/xvdb
    break
  fi
  sleep 2
done
[ -z "$DATA_DEVICE" ] && { echo "ERROR: data volume not found after 60s"; exit 1; }

# 최초 1회만 포맷 (기존 데이터가 있으면 건너뜀)
if ! blkid "$DATA_DEVICE" | grep -q TYPE; then
  mkfs.ext4 -L pg-data "$DATA_DEVICE"
fi

mkdir -p /var/lib/pgsql
mount "$DATA_DEVICE" /var/lib/pgsql

# fstab에 UUID로 등록 (재부팅 후 자동 마운트, 중복 방지)
DATA_UUID=$(blkid -s UUID -o value "$DATA_DEVICE")
grep -q "$DATA_UUID" /etc/fstab || echo "UUID=$DATA_UUID /var/lib/pgsql ext4 defaults,nofail 0 2" >> /etc/fstab

# --- PostgreSQL 16 ---
dnf install -y postgresql16-server postgresql16

# 신규 볼륨이면 초기화, 기존 데이터가 있으면 바로 시작
if [ ! -f /var/lib/pgsql/data/PG_VERSION ]; then
  chown postgres:postgres /var/lib/pgsql
  postgresql-setup --initdb

  # PostgreSQL 설정: localhost만 리슨
  sed -i "s/#listen_addresses = 'localhost'/listen_addresses = 'localhost'/" /var/lib/pgsql/data/postgresql.conf
  sed -i "s/#max_connections = 100/max_connections = 30/" /var/lib/pgsql/data/postgresql.conf
  echo "effective_cache_size = 512MB" >> /var/lib/pgsql/data/postgresql.conf

  # pg_hba.conf: local 인증
  cat > /var/lib/pgsql/data/pg_hba.conf << 'PGHBA'
# TYPE  DATABASE    USER    ADDRESS     METHOD
local   all         all                 peer
host    all         all     127.0.0.1/32 md5
host    all         all     ::1/128      md5
PGHBA

  systemctl enable postgresql
  systemctl start postgresql

  # DB 및 사용자 생성
  sudo -u postgres psql -c "ALTER USER postgres PASSWORD '${DB_PASSWORD}';"
  sudo -u postgres psql -c "CREATE DATABASE jobplanet OWNER postgres;"
  sudo -u postgres psql -d jobplanet -c "CREATE EXTENSION IF NOT EXISTS pg_trgm;"
else
  # 기존 데이터 볼륨 - owner 보정 후 시작 (새 인스턴스의 postgres UID 불일치 방지)
  chown -R postgres:postgres /var/lib/pgsql
  grep -q "^max_connections = 30" /var/lib/pgsql/data/postgresql.conf || \
    sed -i "s/^max_connections = .*/max_connections = 30/" /var/lib/pgsql/data/postgresql.conf
  grep -q "^effective_cache_size" /var/lib/pgsql/data/postgresql.conf || \
    echo "effective_cache_size = 512MB" >> /var/lib/pgsql/data/postgresql.conf
  systemctl enable postgresql
  systemctl start postgresql
fi

# --- Google Chrome (Selenium용, module-app에서 사용) ---
cat > /etc/yum.repos.d/google-chrome.repo << 'CHROME'
[google-chrome]
name=google-chrome
baseurl=https://dl.google.com/linux/chrome/rpm/stable/x86_64
enabled=1
gpgcheck=1
gpgkey=https://dl.google.com/linux/linux_signing_key.pub
CHROME

dnf install -y google-chrome-stable || {
  dnf install -y wget
  wget -q https://dl.google.com/linux/direct/google-chrome-stable_current_x86_64.rpm
  dnf install -y ./google-chrome-stable_current_x86_64.rpm
  rm -f google-chrome-stable_current_x86_64.rpm
}

# --- 앱/설정 디렉토리 ---
mkdir -p /opt/crawling-planet
mkdir -p /opt/crawling-planet/bin
mkdir -p /var/log/crawling-planet
mkdir -p /etc/crawling-planet
chown ec2-user:ec2-user /opt/crawling-planet
chown -R ec2-user:ec2-user /var/log/crawling-planet

# --- Environment 파일 ---
cat > /etc/crawling-planet/app.env << EOF
DB_USERNAME=postgres
DB_PASSWORD=${DB_PASSWORD}
SPRING_DATASOURCE_URL=jdbc:postgresql://localhost:5432/jobplanet
# CRAWLING_ADMIN_TOKEN=
# JOBPLANET_EMAIL=
# JOBPLANET_PASSWORD=
# JOBPLANET_SSR_AUTH=
EOF

cat > /etc/crawling-planet/api.env << EOF
DB_USERNAME=postgres
DB_PASSWORD=${DB_PASSWORD}
SPRING_DATASOURCE_URL=jdbc:postgresql://localhost:5432/jobplanet
# API_KEY=
EOF

chmod 600 /etc/crawling-planet/app.env /etc/crawling-planet/api.env

# --- Nginx reverse proxy ---
cat > /etc/nginx/conf.d/crawling-planet.conf << 'NGINX'
server {
    listen 80 default_server;
    server_name crawling-planet.cc _;

    location /api/ext/ {
        proxy_pass http://127.0.0.1:8081;
        proxy_http_version 1.1;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
    }

    location /api/crawling/ {
        proxy_pass http://127.0.0.1:8080;
        proxy_http_version 1.1;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
    }

    location = /health/app {
        proxy_pass http://127.0.0.1:8080/api/crawling/status;
    }

    location = /health/api {
        proxy_pass http://127.0.0.1:8081/health;
    }

    location = /health/nginx {
        default_type text/plain;
        return 200 "ok\n";
    }
}
NGINX

rm -f /etc/nginx/conf.d/default.conf
nginx -t
systemctl enable nginx
systemctl restart nginx

# --- systemd: module-app (크롤러, 포트 8080) ---
cat > /etc/systemd/system/crawling-planet-app.service << 'SERVICE'
[Unit]
Description=Crawling Planet - Crawler App (port 8080)
After=postgresql.service network.target
Requires=postgresql.service

[Service]
Type=simple
User=ec2-user
WorkingDirectory=/opt/crawling-planet
EnvironmentFile=/etc/crawling-planet/app.env
ExecStart=/usr/bin/java -Xms256m -Xmx768m -XX:MaxDirectMemorySize=384m -XX:+UseG1GC -XX:MaxGCPauseMillis=200 -jar /opt/crawling-planet/app.jar --spring.profiles.active=prod
Restart=on-failure
RestartSec=10
SyslogIdentifier=crawling-planet-app
StandardOutput=journal
StandardError=journal

[Install]
WantedBy=multi-user.target
SERVICE

# --- systemd: module-api (익스텐션 API, 포트 8081) ---
cat > /etc/systemd/system/crawling-planet-api.service << 'SERVICE'
[Unit]
Description=Crawling Planet - API Server (port 8081)
After=postgresql.service network.target
Requires=postgresql.service

[Service]
Type=simple
User=ec2-user
WorkingDirectory=/opt/crawling-planet
EnvironmentFile=/etc/crawling-planet/api.env
ExecStart=/usr/bin/java -Xms128m -Xmx384m -XX:MaxDirectMemorySize=128m -XX:+UseG1GC -jar /opt/crawling-planet/api.jar --spring.profiles.active=prod
Restart=on-failure
RestartSec=10
SyslogIdentifier=crawling-planet-api
StandardOutput=journal
StandardError=journal

[Install]
WantedBy=multi-user.target
SERVICE

# --- healthcheck 스크립트 + timer ---
cat > /opt/crawling-planet/bin/check-crawling-health.sh << 'SCRIPT'
#!/bin/bash
set -euo pipefail

LOG_FILE="/var/log/crawling-planet/healthcheck.log"

timestamp() {
  date '+%Y-%m-%d %H:%M:%S'
}

log() {
  echo "[$(timestamp)] $1" >> "$LOG_FILE"
}

check_http() {
  local service_name="$1"
  local url="$2"

  if curl --silent --show-error --fail --max-time 10 "$url" >/dev/null; then
    log "$service_name healthy ($url)"
    return 0
  fi

  log "$service_name unhealthy ($url) -> restarting"
  systemctl restart "$service_name"
}

check_systemd() {
  local service_name="$1"

  if systemctl is-active --quiet "$service_name"; then
    log "$service_name active"
    return 0
  fi

  log "$service_name inactive -> restarting"
  systemctl restart "$service_name"
}

check_http "crawling-planet-app.service" "http://127.0.0.1:8080/api/crawling/status"
check_systemd "crawling-planet-api.service"
SCRIPT

chmod +x /opt/crawling-planet/bin/check-crawling-health.sh

cat > /etc/systemd/system/crawling-planet-healthcheck.service << 'SERVICE'
[Unit]
Description=Crawling Planet healthcheck
After=network.target

[Service]
Type=oneshot
ExecStart=/opt/crawling-planet/bin/check-crawling-health.sh
SERVICE

cat > /etc/systemd/system/crawling-planet-healthcheck.timer << 'TIMER'
[Unit]
Description=Run Crawling Planet healthcheck every 5 minutes

[Timer]
OnBootSec=2min
OnUnitActiveSec=5min
Unit=crawling-planet-healthcheck.service

[Install]
WantedBy=timers.target
TIMER

systemctl daemon-reload
systemctl enable crawling-planet-healthcheck.timer
systemctl start crawling-planet-healthcheck.timer

# --- pg_dump 백업 cron (매일 03:00) ---
cat > /etc/cron.d/pg-backup << 'CRON'
0 3 * * * postgres pg_dump -Fc jobplanet | /usr/bin/aws s3 cp - s3://${BACKUP_BUCKET}/jobplanet-$(date +\%Y\%m\%d-\%H\%M).dump
CRON
chmod 644 /etc/cron.d/pg-backup

# --- 완료 ---
echo "Bootstrap completed at $(date)" > /opt/crawling-planet/bootstrap.log
