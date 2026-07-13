# ECS Fargate — 앱 실행 레이어 (EC2+SSM 배포에서 전환)
# 구조: cluster → task definition(컨테이너 명세) → service(개수 유지·롤링·ALB 연동)
#
# 역할이 2개인 이유 (면접 단골):
#   execution role = "ECS가 컨테이너를 띄울 때" 쓰는 권한 (ECR pull, 로그 쓰기, secret 주입)
#   task role      = "앱 코드가 AWS를 부를 때" 쓰는 권한 (S3 업로드, KMS)
# 컨테이너 탈취돼도 execution 권한은 앱에 없음 — 최소권한 분리.

resource "aws_ecs_cluster" "main" {
  name = "${var.project}-${var.env}"

  setting {
    name  = "containerInsights"
    value = "enabled" # 태스크 단위 CPU/메모리 메트릭
  }
}

resource "aws_cloudwatch_log_group" "api" {
  name              = "/ecs/${var.project}-api-${var.env}"
  retention_in_days = 30
}

# ── 공통 assume policy ────────────────────────────────
data "aws_iam_policy_document" "ecs_tasks_assume" {
  statement {
    actions = ["sts:AssumeRole"]
    principals {
      type        = "Service"
      identifiers = ["ecs-tasks.amazonaws.com"]
    }
  }
}

# ── execution role: 컨테이너 기동용 ───────────────────
resource "aws_iam_role" "ecs_execution" {
  name               = "${var.project}-ecs-execution-${var.env}"
  assume_role_policy = data.aws_iam_policy_document.ecs_tasks_assume.json
}

resource "aws_iam_role_policy_attachment" "ecs_execution_managed" {
  role       = aws_iam_role.ecs_execution.name
  policy_arn = "arn:aws:iam::aws:policy/service-role/AmazonECSTaskExecutionRolePolicy" # ECR pull + CloudWatch Logs
}

# taskdef의 secrets(valueFrom)로 SSM 파라미터를 컨테이너 env에 주입할 때 필요
data "aws_iam_policy_document" "ecs_execution_secrets" {
  statement {
    actions   = ["ssm:GetParameters"]
    resources = ["arn:aws:ssm:${var.region}:${data.aws_caller_identity.me.account_id}:parameter/${var.project}/${var.env}/*"]
  }
}

resource "aws_iam_role_policy" "ecs_execution_secrets" {
  name   = "read-app-secrets"
  role   = aws_iam_role.ecs_execution.id
  policy = data.aws_iam_policy_document.ecs_execution_secrets.json
}

# ── task role: 앱 런타임용 (기존 EC2 롤의 app_runtime에서 이관) ──
resource "aws_iam_role" "ecs_task" {
  name               = "${var.project}-ecs-task-${var.env}"
  assume_role_policy = data.aws_iam_policy_document.ecs_tasks_assume.json
}

data "aws_iam_policy_document" "ecs_task_runtime" {
  # 업로드/presign — media·docs 버킷
  statement {
    actions = ["s3:PutObject", "s3:GetObject", "s3:DeleteObject"]
    resources = [
      "${aws_s3_bucket.media.arn}/*",
      "${aws_s3_bucket.docs.arn}/*",
    ]
  }

  statement {
    actions   = ["kms:Decrypt", "kms:GenerateDataKey"]
    resources = [aws_kms_key.docs.arn]
  }
}

resource "aws_iam_role_policy" "ecs_task_runtime" {
  name   = "app-runtime"
  role   = aws_iam_role.ecs_task.id
  policy = data.aws_iam_policy_document.ecs_task_runtime.json
}

# ── task definition ───────────────────────────────────
resource "aws_ecs_task_definition" "api" {
  family                   = "${var.project}-api-${var.env}"
  requires_compatibilities = ["FARGATE"]
  network_mode             = "awsvpc"
  cpu                      = 1024 # 1 vCPU
  memory                   = 2048 # 2GB — Spring Boot 안전선. 힙 튜닝 후 1GB 축소 검토
  execution_role_arn       = aws_iam_role.ecs_execution.arn
  task_role_arn            = aws_iam_role.ecs_task.arn

  runtime_platform {
    operating_system_family = "LINUX"
    cpu_architecture        = "X86_64" # CI가 amd64로 빌드. ARM 전환 시 buildx 필요(비용 -20%)
  }

  container_definitions = jsonencode([{
    name      = "api"
    image     = "${aws_ecr_repository.app.repository_url}:bootstrap" # 첫 CI 배포가 실제 태그로 교체
    essential = true

    portMappings = [{ containerPort = var.app_port, protocol = "tcp" }]

    environment = [
      { name = "SPRING_PROFILES_ACTIVE", value = var.env },
      { name = "KAFKA_BOOTSTRAP_SERVERS", value = "${local.kafka_private_ip}:9092" },
      # Spring Boot 3 relaxed binding: spring.data.redis.host/port
      { name = "SPRING_DATA_REDIS_HOST", value = aws_elasticache_cluster.redis.cache_nodes[0].address },
      { name = "SPRING_DATA_REDIS_PORT", value = "6379" },
    ]

    # SSM SecureString → 컨테이너 env로 주입 (이미지·코드에 비밀 없음)
    secrets = [
      { name = "SPRING_DATASOURCE_URL", valueFrom = aws_ssm_parameter.db_url.arn },
      { name = "SPRING_DATASOURCE_USERNAME", valueFrom = aws_ssm_parameter.db_username.arn },
      { name = "SPRING_DATASOURCE_PASSWORD", valueFrom = aws_ssm_parameter.db_password.arn },
    ]

    logConfiguration = {
      logDriver = "awslogs"
      options = {
        awslogs-group         = aws_cloudwatch_log_group.api.name
        awslogs-region        = var.region
        awslogs-stream-prefix = "api"
      }
    }
  }])
}

# ── service: 롤링 배포 + 실패 시 자동 롤백 ─────────────
resource "aws_ecs_service" "api" {
  name            = "${var.project}-api-${var.env}"
  cluster         = aws_ecs_cluster.main.id
  task_definition = aws_ecs_task_definition.api.arn
  desired_count   = 1
  launch_type     = "FARGATE"

  # 새 리비전 배포가 헬스체크 실패하면 이전 리비전으로 자동 롤백
  deployment_circuit_breaker {
    enable   = true
    rollback = true
  }

  network_configuration {
    subnets          = [aws_subnet.app_a.id]
    security_groups  = [aws_security_group.app.id]
    assign_public_ip = false # private 서브넷 + NAT
  }

  load_balancer {
    target_group_arn = aws_lb_target_group.app.arn
    container_name   = "api"
    container_port   = var.app_port
  }

  health_check_grace_period_seconds = 90 # Spring 부팅 시간 유예

  # task_definition: CI가 새 리비전 등록 → TF가 되돌리지 않게
  # desired_count: 오토스케일링이 관리
  lifecycle {
    ignore_changes = [task_definition, desired_count]
  }
}

# ── 오토스케일링: CPU 70% 타겟 트래킹 ──────────────────
resource "aws_appautoscaling_target" "api" {
  service_namespace  = "ecs"
  resource_id        = "service/${aws_ecs_cluster.main.name}/${aws_ecs_service.api.name}"
  scalable_dimension = "ecs:service:DesiredCount"
  min_capacity       = 1
  max_capacity       = 3
}

resource "aws_appautoscaling_policy" "api_cpu" {
  name               = "cpu-target-70"
  service_namespace  = aws_appautoscaling_target.api.service_namespace
  resource_id        = aws_appautoscaling_target.api.resource_id
  scalable_dimension = aws_appautoscaling_target.api.scalable_dimension
  policy_type        = "TargetTrackingScaling"

  target_tracking_scaling_policy_configuration {
    predefined_metric_specification {
      predefined_metric_type = "ECSServiceAverageCPUUtilization"
    }
    target_value       = 70
    scale_in_cooldown  = 120
    scale_out_cooldown = 60
  }
}
