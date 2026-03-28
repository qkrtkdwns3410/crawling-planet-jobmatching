# S3 - PostgreSQL 백업용
resource "aws_s3_bucket" "backup" {
  bucket = "${var.project_name}-db-backup-${data.aws_caller_identity.current.account_id}"

  tags = {
    Name = "${var.project_name}-db-backup"
  }
}

# 퍼블릭 접근 차단
resource "aws_s3_bucket_public_access_block" "backup" {
  bucket = aws_s3_bucket.backup.id

  block_public_acls       = true
  block_public_policy     = true
  ignore_public_acls      = true
  restrict_public_buckets = true
}

# 버전 관리 비활성화 (비용 절감)
resource "aws_s3_bucket_versioning" "backup" {
  bucket = aws_s3_bucket.backup.id

  versioning_configuration {
    status = "Disabled"
  }
}

# 수명 주기 - 30일 후 삭제 (백업 보관)
resource "aws_s3_bucket_lifecycle_configuration" "backup" {
  bucket = aws_s3_bucket.backup.id

  rule {
    id     = "cleanup-old-backups"
    status = "Enabled"

    filter {}

    expiration {
      days = 30
    }
  }
}

# 서버 사이드 암호화
resource "aws_s3_bucket_server_side_encryption_configuration" "backup" {
  bucket = aws_s3_bucket.backup.id

  rule {
    apply_server_side_encryption_by_default {
      sse_algorithm = "AES256"
    }
  }
}

data "aws_caller_identity" "current" {}
