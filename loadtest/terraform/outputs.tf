# terraform output -json 키는 kit 스크립트가 그대로 읽는다
locals {
  host_out = { for k, h in aws_instance.host : k => { instance_id = h.id, private_ip = h.private_ip } }
}

output "region" {
  value = var.region
}

output "bucket" {
  value = aws_s3_bucket.artifacts.bucket
}

output "runner" {
  value = local.host_out["runner"]
}

output "api" {
  value = local.host_out["api"]
}

output "es" {
  value = lookup(local.host_out, "es", null)
}

output "rds" {
  value = {
    for k, db in aws_db_instance.perf : k => {
      id             = db.identifier
      address        = db.address
      port           = db.port
      instance_class = db.instance_class
    }
  }
}

output "mirror" {
  value = length(aws_db_instance.mirror) == 0 ? null : {
    id             = aws_db_instance.mirror[0].identifier
    address        = aws_db_instance.mirror[0].address
    port           = aws_db_instance.mirror[0].port
    instance_class = aws_db_instance.mirror[0].instance_class
  }
}

output "db_username" {
  value = local.db_username
}

output "db_password_param" {
  value = aws_ssm_parameter.db_master.name
}

output "exporter_password_param" {
  value = aws_ssm_parameter.exporter.name
}

output "grafana_password_param" {
  value = aws_ssm_parameter.grafana.name
}

output "api_log_group" {
  value = aws_cloudwatch_log_group.api.name
}
