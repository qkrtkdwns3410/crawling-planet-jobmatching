<!-- Parent: ../AGENTS.md -->
<!-- Generated: 2026-04-01 | Updated: 2026-04-05 -->

# infra

## Purpose
AWS 인프라를 Terraform으로 관리하는 디렉토리. EC2 Spot 인스턴스, S3 버킷, IAM 역할, VPC/보안그룹, 배포 스크립트를 포함한다.

## Key Files

| File | Description |
|------|-------------|
| `terraform/main.tf` | Terraform 프로바이더 설정 (AWS ap-northeast-2) |
| `terraform/variables.tf` | 변수 정의 (DB 비밀번호 등) |
| `terraform/terraform.tfvars` | 변수 값 (민감 정보 — gitignore 대상) |
| `terraform/vpc.tf` | VPC, 서브넷, 인터넷 게이트웨이, 라우팅 테이블 |
| `terraform/security_groups.tf` | 보안 그룹 (SSH 22, HTTP 80/443, 앱 8080/8081) |
| `terraform/ec2.tf` | EC2 Spot t3.small, Elastic IP, 키페어 |
| `terraform/s3.tf` | S3 버킷 (DB 백업, 30일 보관) |
| `terraform/iam.tf` | IAM 역할 및 정책 (EC2 → S3 접근) |
| `terraform/outputs.tf` | 출력 값 (EC2 IP, S3 버킷명 등) |
| `terraform/user_data.sh` | EC2 초기화 스크립트 (Java 설치, systemd 서비스 등록) |

## Subdirectories

| Directory | Purpose |
|-----------|---------|
| `terraform/` | Terraform IaC 코드 전체 |

## For AI Agents

### Infrastructure Overview
```
인터넷
  └── Cloudflare (DNS + Proxy + Flexible SSL)
      └── crawling-planet.cc
          └── EC2 Elastic IP: 43.203.47.167
              └── Nginx (80/443)
                  ├── /api/crawling/* → localhost:8080 (module-app)
                  └── /api/ext/*      → localhost:8081 (module-api)
                      └── PostgreSQL 16 (localhost:5432)
```

### Key Infrastructure Details
- **리전**: ap-northeast-2 (서울)
- **인스턴스**: EC2 Spot t3.small
- **OS**: Amazon Linux 2023
- **Java**: Java 21 (user_data.sh에서 설치)
- **DB**: PostgreSQL 16 (EC2 내부 localhost:5432)
- **DB 백업**: S3 일일 pg_dump (cron, 30일 보관)
- **도메인**: crawling-planet.cc (Cloudflare Flexible SSL)

### Deployment
- GitHub Actions 수동 트리거 (`workflow_dispatch`) 또는 SCP + systemd restart
- systemd 서비스: `crawling-planet-app.service`, `crawling-planet-api.service`
- 배포 workflow: `.github/workflows/deploy.yml`

### Working In This Directory
- `terraform.tfvars`는 gitignore됨 — DB 비밀번호 등 민감 정보 포함
- `terraform.tfstate`는 로컬 상태 파일 — 팀 협업 시 S3 remote state 고려
- Terraform 적용: `terraform init && terraform plan && terraform apply`
- `user_data.sh` 수정 후 EC2 재생성 필요 (user_data는 인스턴스 초기화 시에만 실행)

### Testing Requirements
- `terraform plan`으로 변경사항 미리 확인
- EC2 변경 전 스냅샷/백업 권장

## Dependencies

### External
- AWS (EC2, S3, VPC, IAM)
- Cloudflare (DNS, SSL)
- Terraform ~> 5.x

<!-- MANUAL: -->
