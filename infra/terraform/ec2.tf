# Amazon Linux 2023 AMI (최신)
data "aws_ami" "al2023" {
  most_recent = true
  owners      = ["amazon"]

  filter {
    name   = "name"
    values = ["al2023-ami-*-x86_64"]
  }

  filter {
    name   = "virtualization-type"
    values = ["hvm"]
  }

  filter {
    name   = "architecture"
    values = ["x86_64"]
  }
}

# EC2 Spot Instance
resource "aws_spot_instance_request" "app" {
  ami                    = data.aws_ami.al2023.id
  instance_type          = var.instance_type
  key_name               = var.key_name
  subnet_id              = aws_subnet.public.id
  vpc_security_group_ids = [aws_security_group.app.id]
  iam_instance_profile   = aws_iam_instance_profile.ec2_profile.name

  # Spot 설정
  spot_type            = "persistent"
  wait_for_fulfillment = true

  # EBS Root Volume - 30GB gp3
  root_block_device {
    volume_size           = 30
    volume_type           = "gp3"
    iops                  = 3000
    throughput            = 125
    delete_on_termination = true
    encrypted             = true
  }

  # User Data - 부트스트랩 스크립트
  user_data = base64encode(templatefile("${path.module}/user_data.sh", {
    DB_PASSWORD   = var.db_password
    BACKUP_BUCKET = aws_s3_bucket.backup.id
  }))

  tags = {
    Name = "${var.project_name}-app"
  }
}

# Spot 인스턴스에 태그 전파
resource "aws_ec2_tag" "app_name" {
  resource_id = aws_spot_instance_request.app.spot_instance_id
  key         = "Name"
  value       = "${var.project_name}-app"
}

# Elastic IP
resource "aws_eip" "app" {
  domain = "vpc"

  tags = {
    Name = "${var.project_name}-eip"
  }
}

# EIP를 Spot 인스턴스에 연결
resource "aws_eip_association" "app" {
  instance_id   = aws_spot_instance_request.app.spot_instance_id
  allocation_id = aws_eip.app.id
}
