locals {
  db_username = "perf_admin"

  # slowquery 로그 그룹을 미리 만들 RDS 식별자
  rds_ids = concat(
    [for d in var.datasets : "aechak-perf-${d}"],
    var.mirror_snapshot_id == null ? [] : ["aechak-perf-mirror"],
  )

  # 팀 값에 측정용 값을 더한다. static은 재부팅 뒤 적용된다
  db_parameters = {
    character_set_server                   = { value = "utf8mb4", apply = "immediate" }
    collation_server                       = { value = "utf8mb4_0900_ai_ci", apply = "immediate" }
    time_zone                              = { value = "Asia/Seoul", apply = "immediate" }
    performance_schema                     = { value = "1", apply = "pending-reboot" }
    max_digest_length                      = { value = "4096", apply = "pending-reboot" }
    performance_schema_max_digest_length   = { value = "4096", apply = "pending-reboot" }
    performance_schema_max_sql_text_length = { value = "4096", apply = "pending-reboot" }
    slow_query_log                         = { value = "1", apply = "immediate" }
    long_query_time                        = { value = "0.1", apply = "immediate" }
    log_output                             = { value = "FILE", apply = "immediate" }
  }
}

resource "aws_db_subnet_group" "perf" {
  name       = "aechak-perf"
  subnet_ids = [data.aws_subnet.data_a.id, data.aws_subnet.data_c.id]
}

resource "aws_db_parameter_group" "perf" {
  name   = "aechak-perf-mysql84"
  family = "mysql8.4"

  dynamic "parameter" {
    for_each = local.db_parameters
    content {
      name         = parameter.key
      value        = parameter.value.value
      apply_method = parameter.value.apply
    }
  }
}

resource "aws_iam_role" "rds_monitoring" {
  name = "aechak-perf-rds-monitoring"
  assume_role_policy = jsonencode({
    Version = "2012-10-17"
    Statement = [{
      Effect    = "Allow"
      Action    = "sts:AssumeRole"
      Principal = { Service = "monitoring.rds.amazonaws.com" }
    }]
  })
}

resource "aws_iam_role_policy_attachment" "rds_monitoring" {
  role       = aws_iam_role.rds_monitoring.name
  policy_arn = "arn:aws:iam::aws:policy/service-role/AmazonRDSEnhancedMonitoringRole"
}

# RDS가 slowquery를 내보내기 전에 보존 기간을 정해 둔다
resource "aws_cloudwatch_log_group" "slowquery" {
  for_each          = toset(local.rds_ids)
  name              = "/aws/rds/instance/${each.key}/slowquery"
  retention_in_days = 14
}

resource "aws_db_instance" "perf" {
  for_each = toset(var.datasets)

  identifier                 = "aechak-perf-${each.key}"
  engine                     = "mysql"
  engine_version             = var.db_engine_version
  auto_minor_version_upgrade = false
  instance_class             = var.db_instance_class

  db_name  = "aechak"
  username = local.db_username
  password = random_password.db.result

  allocated_storage = lookup(var.db_storage_gb, each.key, 20)
  storage_type      = "gp3"
  storage_encrypted = true

  # EC2와 같은 AZ에 둔다
  availability_zone      = data.aws_subnet.public_a.availability_zone
  multi_az               = false
  db_subnet_group_name   = aws_db_subnet_group.perf.name
  vpc_security_group_ids = [aws_security_group.rds.id]
  parameter_group_name   = aws_db_parameter_group.perf.name

  backup_retention_period = 0
  skip_final_snapshot     = true
  deletion_protection     = false
  apply_immediately       = true
  copy_tags_to_snapshot   = true

  # 7일 보존은 Database Insights 표준 모드 무료 범위
  performance_insights_enabled          = true
  performance_insights_retention_period = 7

  monitoring_interval             = 15
  monitoring_role_arn             = aws_iam_role.rds_monitoring.arn
  enabled_cloudwatch_logs_exports = ["slowquery"]

  depends_on = [aws_cloudwatch_log_group.slowquery, aws_iam_role_policy_attachment.rds_monitoring]

  tags = { Name = "aechak-perf-${each.key}" }
}

# 운영 거울층. micro는 Performance Insights를 지원하지 않는다
resource "aws_db_instance" "mirror" {
  count = var.mirror_snapshot_id == null ? 0 : 1

  identifier                 = "aechak-perf-mirror"
  snapshot_identifier        = var.mirror_snapshot_id
  auto_minor_version_upgrade = false
  instance_class             = "db.t4g.micro"
  storage_type               = "gp3"

  availability_zone      = data.aws_subnet.public_a.availability_zone
  multi_az               = false
  db_subnet_group_name   = aws_db_subnet_group.perf.name
  vpc_security_group_ids = [aws_security_group.rds.id]
  parameter_group_name   = aws_db_parameter_group.perf.name

  backup_retention_period = 0
  skip_final_snapshot     = true
  deletion_protection     = false
  apply_immediately       = true

  monitoring_interval             = 15
  monitoring_role_arn             = aws_iam_role.rds_monitoring.arn
  enabled_cloudwatch_logs_exports = ["slowquery"]

  depends_on = [aws_cloudwatch_log_group.slowquery, aws_iam_role_policy_attachment.rds_monitoring]

  tags = { Name = "aechak-perf-mirror" }
}

resource "random_password" "db" {
  length  = 24
  special = false
}

resource "random_password" "exporter" {
  length  = 24
  special = false
}

resource "random_password" "grafana" {
  length  = 24
  special = false
}

resource "aws_ssm_parameter" "db_master" {
  name  = "/aechak-perf/db/master-password"
  type  = "SecureString"
  value = random_password.db.result
}

resource "aws_ssm_parameter" "exporter" {
  name  = "/aechak-perf/db/exporter-password"
  type  = "SecureString"
  value = random_password.exporter.result
}

resource "aws_ssm_parameter" "grafana" {
  name  = "/aechak-perf/grafana/admin-password"
  type  = "SecureString"
  value = random_password.grafana.result
}

resource "aws_cloudwatch_log_group" "api" {
  name              = "/perf/aechak-api"
  retention_in_days = 14
}
