# 매일 정해진 시각에 perf EC2와 RDS를 멈춘다. 이미 멈춰 있으면 호출만 실패하고 끝난다
locals {
  auto_stop_rds = var.auto_stop_enabled ? toset(local.rds_ids) : toset([])
}

resource "aws_iam_role" "auto_stop" {
  count = var.auto_stop_enabled ? 1 : 0
  name  = "aechak-perf-auto-stop"
  assume_role_policy = jsonencode({
    Version = "2012-10-17"
    Statement = [{
      Effect    = "Allow"
      Action    = "sts:AssumeRole"
      Principal = { Service = "scheduler.amazonaws.com" }
      Condition = { StringEquals = { "aws:SourceAccount" = local.account_id } }
    }]
  })
}

resource "aws_iam_role_policy" "auto_stop" {
  count = var.auto_stop_enabled ? 1 : 0
  name  = "aechak-perf-auto-stop"
  role  = aws_iam_role.auto_stop[0].id
  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        Effect   = "Allow"
        Action   = "rds:StopDBInstance"
        Resource = [for id in local.auto_stop_rds : "arn:aws:rds:${var.region}:${local.account_id}:db:${id}"]
      },
      {
        Effect   = "Allow"
        Action   = "ec2:StopInstances"
        Resource = [for h in aws_instance.host : h.arn]
      },
    ]
  })
}

resource "aws_scheduler_schedule" "stop_rds" {
  for_each = local.auto_stop_rds
  name     = "${each.key}-stop"

  schedule_expression          = var.auto_stop_cron
  schedule_expression_timezone = "Asia/Seoul"

  flexible_time_window {
    mode = "OFF"
  }

  target {
    arn      = "arn:aws:scheduler:::aws-sdk:rds:stopDBInstance"
    role_arn = aws_iam_role.auto_stop[0].arn
    input    = jsonencode({ DbInstanceIdentifier = each.key })

    retry_policy {
      maximum_retry_attempts       = 0
      maximum_event_age_in_seconds = 60
    }
  }
}

resource "aws_scheduler_schedule" "stop_ec2" {
  count = var.auto_stop_enabled ? 1 : 0
  name  = "aechak-perf-ec2-stop"

  schedule_expression          = var.auto_stop_cron
  schedule_expression_timezone = "Asia/Seoul"

  flexible_time_window {
    mode = "OFF"
  }

  target {
    arn      = "arn:aws:scheduler:::aws-sdk:ec2:stopInstances"
    role_arn = aws_iam_role.auto_stop[0].arn
    input    = jsonencode({ InstanceIds = [for h in aws_instance.host : h.id] })

    retry_policy {
      maximum_retry_attempts       = 0
      maximum_event_age_in_seconds = 60
    }
  }
}
