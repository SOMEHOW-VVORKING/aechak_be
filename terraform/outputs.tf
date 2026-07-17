output "alb_dns_name" {
  value = aws_lb.main.dns_name
}

output "ecr_repository_url" {
  value = aws_ecr_repository.app.repository_url
}

output "rds_endpoint" {
  value = aws_db_instance.main.address
}

output "kafka_bootstrap" {
  value = "${local.kafka_private_ip}:9092"
}

output "media_bucket" {
  value = aws_s3_bucket.media.id
}

output "docs_bucket" {
  value = aws_s3_bucket.docs.id
}

output "cloudfront_domain" {
  value = aws_cloudfront_distribution.media.domain_name
}

output "ecs_cluster_name" {
  value = aws_ecs_cluster.main.name
}

output "ecs_service_name" {
  value = aws_ecs_service.api.name
}

output "app_log_group" {
  value       = aws_cloudwatch_log_group.api.name
  description = "앱은 ECS — 인스턴스 접속 대신 CloudWatch Logs로 본다"
}
