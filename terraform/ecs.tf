# ECS Fargate — 앱 실행 레이어 (EC2+SSM 배포에서 전환)
# 구조: cluster → task definition(컨테이너 명세) → service(개수 유지·롤링·ALB 연동)
#
# 역할이 2개인 이유 (면접 단골):
#   execution role = "ECS가 컨테이너를 띄울 때" 쓰는 권한 (ECR pull, 로그 쓰기)
#   task role      = "앱 코드가 AWS를 부를 때" 쓰는 권한 (S3 업로드, KMS, SSM 설정 조회)
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

  # 앱이 부팅 시 /aechak/dev/api/ 경로를 직접 조회 (Spring Cloud AWS는 GetParametersByPath 사용)
  # 없으면 부팅 시 파라미터를 못 읽어 datasource 설정이 비고 앱이 기동 실패한다
  statement {
    actions = ["ssm:GetParametersByPath"] # 앱은 경로 조회만 씀 — 리프 단건 조회(GetParameters) 권한은 잉여라 제외
    resources = [
      "arn:aws:ssm:${var.region}:${data.aws_caller_identity.me.account_id}:parameter/${var.project}/${var.env}/api",
      "arn:aws:ssm:${var.region}:${data.aws_caller_identity.me.account_id}:parameter/${var.project}/${var.env}/api/*",
      "arn:aws:ssm:${var.region}:${data.aws_caller_identity.me.account_id}:parameter/${var.project}/${var.env}/seller",
      "arn:aws:ssm:${var.region}:${data.aws_caller_identity.me.account_id}:parameter/${var.project}/${var.env}/seller/*",
    ]
  }

  # 문의 접수 통지 메일 발송 — 검증된 발신 도메인, 지정 발신 주소로만
  statement {
    actions   = ["ses:SendEmail"]
    resources = [aws_ses_domain_identity.mail.arn]

    condition {
      test     = "StringEquals"
      variable = "ses:FromAddress"
      values   = [local.ses_from_address]
    }
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

    # 나머지 설정은 앱이 부팅 시 SSM /aechak/dev/api/ 에서 직접 읽는다
    # 프로파일만 env로 주입: import 라인이 프로파일별로 갈리므로 부팅 전에 정해져 있어야 함
    environment = [
      { name = "SPRING_PROFILES_ACTIVE", value = var.env },
      { name = "AWS_REGION", value = var.region }, # Fargate가 자동 주입하지만 플랫폼 암묵 동작에 안 기대고 명시
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

# ── seller-api (SCRUM-193): 셀러센터 실행 모듈 ─────────
# 롤은 api와 공유한다 — 같은 버킷·같은 SSM 트리를 쓰는 동일 신뢰 수준의 웹 모듈이라 분리 실익이 없다.
# 셀러 전용 권한이 갈라지는 시점(정산 이체 등)에 롤 분리를 재검토한다.
resource "aws_cloudwatch_log_group" "seller_api" {
  name              = "/ecs/${var.project}-seller-api-${var.env}"
  retention_in_days = 30
}

resource "aws_ecs_task_definition" "seller_api" {
  family                   = "${var.project}-seller-api-${var.env}"
  requires_compatibilities = ["FARGATE"]
  network_mode             = "awsvpc"
  cpu                      = 512 # api(1024)의 절반 — 셀러 트래픽은 당분간 미미. 상품 등록이 붙으면 재조정
  memory                   = 1024
  execution_role_arn       = aws_iam_role.ecs_execution.arn
  task_role_arn            = aws_iam_role.ecs_task.arn

  runtime_platform {
    operating_system_family = "LINUX"
    cpu_architecture        = "X86_64"
  }

  container_definitions = jsonencode([{
    name      = "seller-api"
    image     = "${aws_ecr_repository.seller_api.repository_url}:bootstrap" # 첫 CI 배포가 실제 태그로 교체
    essential = true

    portMappings = [{ containerPort = var.app_port, protocol = "tcp" }]

    environment = [
      { name = "SPRING_PROFILES_ACTIVE", value = var.env },
      { name = "AWS_REGION", value = var.region },
    ]

    logConfiguration = {
      logDriver = "awslogs"
      options = {
        awslogs-group         = aws_cloudwatch_log_group.seller_api.name
        awslogs-region        = var.region
        awslogs-stream-prefix = "seller-api"
      }
    }
  }])
}

resource "aws_ecs_service" "seller_api" {
  name            = "${var.project}-seller-api-${var.env}"
  cluster         = aws_ecs_cluster.main.id
  task_definition = aws_ecs_task_definition.seller_api.arn
  desired_count   = 1
  launch_type     = "FARGATE"

  deployment_circuit_breaker {
    enable   = true
    rollback = true
  }

  network_configuration {
    subnets          = [aws_subnet.app_a.id]
    security_groups  = [aws_security_group.app.id]
    assign_public_ip = false
  }

  load_balancer {
    target_group_arn = aws_lb_target_group.seller_api.arn
    container_name   = "seller-api"
    container_port   = var.app_port
  }

  health_check_grace_period_seconds = 90

  # 오토스케일링 없음 — 고정 1대. desired_count를 ignore하지 않는 이유이기도 하다(TF가 관리)
  lifecycle {
    ignore_changes = [task_definition] # CI가 새 리비전 등록 — TF가 되돌리지 않게
  }
}
