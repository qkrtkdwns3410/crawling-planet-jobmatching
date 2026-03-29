# Security Group - 최소 권한 원칙
resource "aws_security_group" "app" {
  name        = "${var.project_name}-app-sg"
  description = "Security group for crawling-planet app server"
  vpc_id      = aws_vpc.main.id

  # SSH - GitHub Actions 배포 + 본인 접속용 (키 인증으로 보호)
  ingress {
    description = "SSH"
    from_port   = 22
    to_port     = 22
    protocol    = "tcp"
    cidr_blocks = ["0.0.0.0/0"]
  }

  # App port - 본인 IP만
  ingress {
    description = "App from my IP"
    from_port   = 8080
    to_port     = 8080
    protocol    = "tcp"
    cidr_blocks = ["${var.my_ip}/32"]
  }

  # API port (module-api) - 크롬 익스텐션용, 본인 IP만
  ingress {
    description = "API from my IP"
    from_port   = 8081
    to_port     = 8081
    protocol    = "tcp"
    cidr_blocks = ["${var.my_ip}/32"]
  }

  # HTTP - Cloudflare 프록시에서 들어오는 트래픽 (Nginx)
  ingress {
    description = "HTTP from Cloudflare"
    from_port   = 80
    to_port     = 80
    protocol    = "tcp"
    cidr_blocks = ["0.0.0.0/0"]
  }

  # Outbound - 전체 허용 (크롤링 아웃바운드 필요)
  egress {
    description = "All outbound"
    from_port   = 0
    to_port     = 0
    protocol    = "-1"
    cidr_blocks = ["0.0.0.0/0"]
  }

  tags = {
    Name = "${var.project_name}-app-sg"
  }
}
