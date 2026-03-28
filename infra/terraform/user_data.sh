#!/bin/bash
set -euxo pipefail

# ============================================
# Crawling Planet - EC2 Bootstrap Script
# Amazon Linux 2023 / Java 21 / Chrome / PostgreSQL 16
# 2개 앱: module-app(8080) + module-api(8081)
# ============================================

# --- 시스템 업데이트 ---
dnf update -y

# --- Java 21 (Amazon Corretto) ---
dnf install -y java-21-amazon-corretto-devel

# --- PostgreSQL 16 ---
dnf install -y postgresql16-server postgresql16
postgresql-setup --initdb

# PostgreSQL 설정: localhost만 리슨
sed -i "s/#listen_addresses = 'localhost'/listen_addresses = 'localhost'/" /var/lib/pgsql/data/postgresql.conf

# pg_hba.conf: local 인증
cat > /var/lib/pgsql/data/pg_hba.conf << 'PGHBA'
# TYPE  DATABASE    USER    ADDRESS     METHOD
local   all         all                 peer
host    all         all     127.0.0.1/32 md5
host    all         all     ::1/128      md5
PGHBA

# PostgreSQL 시작
systemctl enable postgresql
systemctl start postgresql

# DB 및 사용자 생성
sudo -u postgres psql -c "ALTER USER postgres PASSWORD '${DB_PASSWORD}';"
sudo -u postgres psql -c "CREATE DATABASE jobplanet OWNER postgres;"
sudo -u postgres psql -d jobplanet -c "CREATE EXTENSION IF NOT EXISTS pg_trgm;"

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

# --- 앱 디렉토리 ---
mkdir -p /opt/crawling-planet
chown ec2-user:ec2-user /opt/crawling-planet

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
ExecStart=/usr/bin/java -Xms512m -Xmx768m -jar /opt/crawling-planet/app.jar --spring.profiles.active=prod
Restart=on-failure
RestartSec=10

Environment=DB_USERNAME=postgres
Environment=DB_PASSWORD=${DB_PASSWORD}
Environment=SPRING_DATASOURCE_URL=jdbc:postgresql://localhost:5432/jobplanet

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
ExecStart=/usr/bin/java -Xms256m -Xmx512m -jar /opt/crawling-planet/api.jar --spring.profiles.active=prod
Restart=on-failure
RestartSec=10

Environment=DB_USERNAME=postgres
Environment=DB_PASSWORD=${DB_PASSWORD}
Environment=SPRING_DATASOURCE_URL=jdbc:postgresql://localhost:5432/jobplanet

[Install]
WantedBy=multi-user.target
SERVICE

systemctl daemon-reload

# --- pg_dump 백업 cron (매일 03:00) ---
cat > /etc/cron.d/pg-backup << 'CRON'
0 3 * * * postgres pg_dump -Fc jobplanet | /usr/bin/aws s3 cp - s3://${BACKUP_BUCKET}/jobplanet-$(date +\%Y\%m\%d-\%H\%M).dump
CRON
chmod 644 /etc/cron.d/pg-backup

# --- 완료 ---
echo "Bootstrap completed at $(date)" > /opt/crawling-planet/bootstrap.log
