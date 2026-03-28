output "spot_instance_id" {
  description = "Spot instance ID"
  value       = aws_spot_instance_request.app.spot_instance_id
}

output "public_ip" {
  description = "Elastic IP address"
  value       = aws_eip.app.public_ip
}

output "ssh_command" {
  description = "SSH command to connect"
  value       = "ssh -i ~/.ssh/${var.key_name}.pem ec2-user@${aws_eip.app.public_ip}"
}

output "app_url" {
  description = "Application URL"
  value       = "http://${aws_eip.app.public_ip}:8080"
}

output "backup_bucket" {
  description = "S3 backup bucket name"
  value       = aws_s3_bucket.backup.id
}
