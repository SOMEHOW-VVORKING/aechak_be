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
      "arn:aws:ssm:${var.region}:${data.aws_caller_identity.me.account_id}:parameter/${var.project}/${var.env}/admin",
      "arn:aws:ssm:${var.region}:${data.aws_caller_identity.me.account_id}:parameter/${var.project}/${var.env}/admin/*",
      "arn:aws:ssm:${var.region}:${data.aws_caller_identity.me.account_id}:parameter/${var.project}/${var.env}/batch",
      "arn:aws:ssm:${var.region}:${data.aws_caller_identity.me.account_id}:parameter/${var.project}/${var.env}/batch/*",
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

  # 부팅(이미지 pull + Spring 기동 ≈ 2분) 완료 전에 unhealthy 판정이 확정되지 않을 만큼 넉넉히.
  # 앱이 일찍 뜨면 그 즉시 healthy로 잡히므로 늘려도 배포가 느려지지 않는다.
  health_check_grace_period_seconds = 300

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

  health_check_grace_period_seconds = 300 # api와 동일 근거 — 부팅 완료 전 unhealthy 확정 방지

  # 오토스케일링 없음 — 고정 1대. desired_count를 ignore하지 않는 이유이기도 하다(TF가 관리)
  lifecycle {
    ignore_changes = [task_definition] # CI가 새 리비전 등록 — TF가 되돌리지 않게
  }
}

# ── admin (SCRUM-229): 어드민 실행 모듈 ────────────────
# 롤·서브넷·SG는 api와 공유 — seller-api와 같은 근거(같은 버킷·SSM 트리를 쓰는 동일 신뢰 수준의 웹 모듈).
resource "aws_cloudwatch_log_group" "admin" {
  name              = "/ecs/${var.project}-admin-${var.env}"
  retention_in_days = 30
}

resource "aws_ecs_task_definition" "admin" {
  family                   = "${var.project}-admin-${var.env}"
  requires_compatibilities = ["FARGATE"]
  network_mode             = "awsvpc"
  cpu                      = 512 # 운영자 소수만 쓰는 모듈 — seller-api와 같은 최소 사양
  memory                   = 1024
  execution_role_arn       = aws_iam_role.ecs_execution.arn
  task_role_arn            = aws_iam_role.ecs_task.arn

  runtime_platform {
    operating_system_family = "LINUX"
    cpu_architecture        = "X86_64"
  }

  container_definitions = jsonencode([{
    name      = "admin"
    image     = "${aws_ecr_repository.admin.repository_url}:bootstrap" # 첫 CI 배포가 실제 태그로 교체
    essential = true

    portMappings = [{ containerPort = var.app_port, protocol = "tcp" }]

    environment = [
      { name = "SPRING_PROFILES_ACTIVE", value = var.env },
      { name = "AWS_REGION", value = var.region },
    ]

    logConfiguration = {
      logDriver = "awslogs"
      options = {
        awslogs-group         = aws_cloudwatch_log_group.admin.name
        awslogs-region        = var.region
        awslogs-stream-prefix = "admin"
      }
    }
  }])
}

resource "aws_ecs_service" "admin" {
  name            = "${var.project}-admin-${var.env}"
  cluster         = aws_ecs_cluster.main.id
  task_definition = aws_ecs_task_definition.admin.arn
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
    target_group_arn = aws_lb_target_group.admin.arn
    container_name   = "admin"
    container_port   = var.app_port
  }

  # 부팅(이미지 pull + Spring 기동 ≈ 2분) 완료 전에 unhealthy 판정이 확정되지 않게 넉넉히
  health_check_grace_period_seconds = 300

  # 오토스케일링 없음 — 고정 1대(TF가 desired_count 관리)
  lifecycle {
    ignore_changes = [task_definition] # CI가 새 리비전 등록 — TF가 되돌리지 않게
  }
}

# ── batch (SCRUM-251): 배치 실행 모듈 ──────────────────
# 웹 모듈이 아니다 — portMappings·load_balancer·health_check_grace_period_seconds가 없다.
# batch는 web starter와 actuator를 싣지 않아 /actuator/health가 없고 수신 포트도 열지 않는다.
# 롤·서브넷·SG는 api와 공유 — 같은 버킷·같은 SSM 트리를 쓰는 동일 신뢰 수준이다.
resource "aws_cloudwatch_log_group" "batch" {
  name              = "/ecs/${var.project}-batch-${var.env}"
  retention_in_days = 30
}
resource "aws_ecs_task_definition" "batch" {
  family                   = "${var.project}-batch-${var.env}"
  requires_compatibilities = ["FARGATE"]
  network_mode             = "awsvpc"
  cpu                      = 512 # 스위퍼 5초 폴링 + 새벽 대사 1회 — admin과 같은 최소 사양
  memory                   = 1024
  execution_role_arn       = aws_iam_role.ecs_execution.arn
  task_role_arn            = aws_iam_role.ecs_task.arn

  runtime_platform {
    operating_system_family = "LINUX"
    cpu_architecture        = "X86_64"
  }

  container_definitions = jsonencode([{
    name      = "batch"
    image     = "${aws_ecr_repository.batch.repository_url}:bootstrap" # 첫 CI 배포가 실제 태그로 교체
    essential = true

    environment = [
      { name = "SPRING_PROFILES_ACTIVE", value = var.env },
      { name = "AWS_REGION", value = var.region },
    ]

    logConfiguration = {
      logDriver = "awslogs"
      options = {
        awslogs-group         = aws_cloudwatch_log_group.batch.name
        awslogs-region        = var.region
        awslogs-stream-prefix = "batch"
      }
    }
  }])
}

resource "aws_ecs_service" "batch" {
  name            = "${var.project}-batch-${var.env}"
  cluster         = aws_ecs_cluster.main.id
  task_definition = aws_ecs_task_definition.batch.arn
  desired_count   = 1 # 고정 1대. 2대 이상이면 같은 스케줄 회차가 동시에 돈다
  launch_type     = "FARGATE"

  deployment_circuit_breaker {
    enable   = true
    rollback = true
  }

  # 신규 태스크를 띄우기 전에 기존 태스크를 먼저 내린다 — 기본값(100/200)이면 롤링 중 잠시 2대가 겹친다
  deployment_minimum_healthy_percent = 0
  deployment_maximum_percent         = 100

  network_configuration {
    subnets          = [aws_subnet.app_a.id]
    security_groups  = [aws_security_group.app.id]
    assign_public_ip = false
  }

  # 오토스케일링 없음 — desired_count는 TF가 1로 고정 관리
  lifecycle {
    ignore_changes = [task_definition] # CI가 새 리비전 등록 — TF가 되돌리지 않게
  }
}

# ── AI 검수 서버 (별 레포 aechak_ai) ──────────────────
# 롤을 자바 세 모듈과 나눈다. 공용 task 롤은 S3 쓰기·삭제와 BE SSM 트리 조회를 들고 있고,
# 검수 서버는 그 어느 것도 필요하지 않다. 권한을 물려받으면 '판정기는 DB와 버킷을 모른다'가
# 선언으로만 남는다.

# 파이썬은 Spring Cloud AWS의 파라미터스토어 로딩을 쓸 수 없어 taskdef secrets로 받는다.
# 그 값을 가져오는 주체는 task 롤이 아니라 execution 롤이고, 경로 조회가 아니라 단건 조회다.
resource "aws_iam_role" "ecs_execution_ai" {
  name               = "${var.project}-ecs-execution-ai-${var.env}"
  assume_role_policy = data.aws_iam_policy_document.ecs_tasks_assume.json
}

resource "aws_iam_role_policy_attachment" "ecs_execution_ai_managed" {
  role       = aws_iam_role.ecs_execution_ai.name
  policy_arn = "arn:aws:iam::aws:policy/service-role/AmazonECSTaskExecutionRolePolicy"
}

data "aws_iam_policy_document" "ecs_execution_ai_secrets" {
  statement {
    actions = ["ssm:GetParameters"] # secrets는 리프 단건 조회를 쓴다 — GetParametersByPath로는 안 된다
    resources = [
      "arn:aws:ssm:${var.region}:${data.aws_caller_identity.me.account_id}:parameter/${var.project}/${var.env}/ai/*",
    ]
  }
}

resource "aws_iam_role_policy" "ecs_execution_ai_secrets" {
  name   = "ai-secrets"
  role   = aws_iam_role.ecs_execution_ai.id
  policy = data.aws_iam_policy_document.ecs_execution_ai_secrets.json
}

# 런타임 권한이 비어 있는 것이 의도다. 검수 서버는 AWS 리소스를 쓰지 않는다 —
# 카프카로만 말하고 외부 모델 API만 부른다. 초안 서비스도 같은 롤을 쓴다.
resource "aws_iam_role" "ecs_task_ai" {
  name               = "${var.project}-ecs-task-ai-${var.env}"
  assume_role_policy = data.aws_iam_policy_document.ecs_tasks_assume.json
}

resource "aws_cloudwatch_log_group" "ai" {
  name              = "/ecs/${var.project}-ai-${var.env}"
  retention_in_days = 30
}

resource "aws_ecs_task_definition" "ai" {
  family                   = "${var.project}-ai-${var.env}"
  requires_compatibilities = ["FARGATE"]
  network_mode             = "awsvpc"
  cpu                      = 512 # 모델 응답을 기다리는 시간이 대부분이라 CPU는 최소로
  memory                   = 1024
  execution_role_arn       = aws_iam_role.ecs_execution_ai.arn
  task_role_arn            = aws_iam_role.ecs_task_ai.arn

  runtime_platform {
    operating_system_family = "LINUX"
    cpu_architecture        = "X86_64"
  }

  container_definitions = jsonencode([{
    name      = "ai"
    image     = "${aws_ecr_repository.ai.repository_url}:bootstrap" # 첫 CI 배포가 실제 태그로 교체
    essential = true

    portMappings = [{ containerPort = var.ai_app_port, protocol = "tcp" }]

    environment = [
      # 이미지가 PORT 하나로 듣는 포트를 정한다. 포트맵·타겟그룹과 같은 변수에서 뽑아야
      # 세 자리가 어긋나지 않는다 — 어긋나면 컨테이너는 떠 있는데 헬스체크가 영구 실패한다.
      { name = "PORT", value = tostring(var.ai_app_port) },
      { name = "AECHAK_AI_KAFKA_BOOTSTRAP_SERVERS", value = "${local.kafka_private_ip}:9092" },
      # 추적이 켜지면 리뷰 본문과 상품 설명이 외부로 송신된다. 이미지 기본값도 false지만
      # 상속으로 켜지는 경로를 막으려고 여기서 한 번 더 못박는다.
      { name = "LANGSMITH_TRACING", value = "false" },
      { name = "LANGCHAIN_TRACING_V2", value = "false" },
    ]

    # 값을 terraform이 알 수 없는 것만 secrets로 받는다. 수기 등재가 전제다.
    secrets = [
      {
        name      = "AECHAK_AI_ANTHROPIC_API_KEY"
        valueFrom = "arn:aws:ssm:${var.region}:${data.aws_caller_identity.me.account_id}:parameter/${var.project}/${var.env}/ai/AECHAK_AI_ANTHROPIC_API_KEY"
      },
    ]

    logConfiguration = {
      logDriver = "awslogs"
      options = {
        awslogs-group         = aws_cloudwatch_log_group.ai.name
        awslogs-region        = var.region
        awslogs-stream-prefix = "ai"
      }
    }
  }])
}

resource "aws_ecs_service" "ai" {
  name            = "${var.project}-ai-${var.env}"
  cluster         = aws_ecs_cluster.main.id
  task_definition = aws_ecs_task_definition.ai.arn
  desired_count   = var.ai_desired_count # 기본 0 — :bootstrap 태그가 없어 1로 두면 실패 루프에 빠진다
  launch_type     = "FARGATE"

  deployment_circuit_breaker {
    enable   = true
    rollback = true
  }

  network_configuration {
    subnets          = [aws_subnet.app_a.id]
    security_groups  = [aws_security_group.ai.id]
    assign_public_ip = false
  }

  load_balancer {
    target_group_arn = aws_lb_target_group.ai.arn
    container_name   = "ai"
    container_port   = var.ai_app_port
  }

  health_check_grace_period_seconds = 120 # 파이썬이라 자바 모듈(300초)보다 기동이 빠르다

  lifecycle {
    ignore_changes = [task_definition] # CI가 새 리비전 등록 — TF가 되돌리지 않게
  }
}

# ── 셀러 폼 초안 API (SCRUM-330): 같은 이미지, 다른 커맨드 ──────────────────
# 검수 컨슈머와 **별 ASGI 앱**이다(`app/draft_api/app.py`). 한 서비스로 합칠 수 없는 이유가
# 레포 쪽에 적혀 있다 — 운영 앱의 라우트 집합은 `/health` 하나로 불변이고, 초안은 셀러가
# 기다리는 동기 HTTP라서 검수 컨슈머의 폴 간격 예산에 들어가면 리밸런스가 **검수**를
# 재배달한다. 그래서 같은 이미지에 CMD와 포트만 달리해 서비스를 하나 더 둔다.
#
# 공개 ALB에 매달지 않는다. 그쪽 타겟그룹에 붙이면 `/internal/seller-draft`가 인터넷에서
# 닿고, 그 경로는 호출마다 모델 비용이 난다. 내부 전용 DNS로만 노출하고 공유 비밀 헤더를
# 둘째 겹으로 둔다(앱이 직접 상수 시간 비교하고, 비밀이 비면 전건 403으로 닫는다).
resource "aws_service_discovery_private_dns_namespace" "internal" {
  name        = "${var.project}-${var.env}.internal"
  vpc         = aws_vpc.main.id
  description = "VPC 내부 전용 이름. 공개 ALB를 지나지 않는 서비스 간 호출에 쓴다"
}

# Fargate 태스크는 고정 IP를 가질 수 없어서 카프카(고정 사설 IP)처럼 주소를 박을 수 없다.
# A 레코드 TTL을 짧게 둬도 JVM이 성공 조회를 30초 캐시하므로, 재배포 직후 최대 그만큼은
# 옛 IP로 간다 — 그 구간은 seller-api가 state=UNAVAILABLE로 접고 빈 폼을 그대로 둔다.
resource "aws_service_discovery_service" "ai_draft" {
  name = "ai-draft"

  dns_config {
    namespace_id   = aws_service_discovery_private_dns_namespace.internal.id
    routing_policy = "MULTIVALUE"

    dns_records {
      type = "A" # awsvpc 태스크 IP를 그대로 등록한다
      ttl  = 10
    }
  }

  # ECS가 태스크 상태로 등록·해제를 관리한다. 컨테이너 헬스체크를 따로 걸지 않아서
  # uvicorn이 포트를 잡기 전 몇 초는 등록된 채 연결 실패할 수 있고, 그 실패도 위와 같이
  # UNAVAILABLE로 접힌다 — 초안은 보조 기능이라 등록을 막을 만큼의 값이 아니다.
  health_check_custom_config {
    failure_threshold = 1
  }
}

resource "aws_cloudwatch_log_group" "ai_draft" {
  name              = "/ecs/${var.project}-ai-draft-${var.env}"
  retention_in_days = 30
}

resource "aws_ecs_task_definition" "ai_draft" {
  family                   = "${var.project}-ai-draft-${var.env}"
  requires_compatibilities = ["FARGATE"]
  network_mode             = "awsvpc"
  cpu                      = 512 # 검수 컨슈머와 같은 근거 — 모델 응답 대기가 대부분이다
  memory                   = 1024
  execution_role_arn       = aws_iam_role.ecs_execution_ai.arn # /ai 트리 단건 조회 권한을 공유
  task_role_arn            = aws_iam_role.ecs_task_ai.arn      # 런타임 AWS 권한 없음 — 초안도 S3를 받지 않는다

  runtime_platform {
    operating_system_family = "LINUX"
    cpu_architecture        = "X86_64"
  }

  container_definitions = jsonencode([{
    name      = "ai-draft"
    image     = "${aws_ecr_repository.ai.repository_url}:bootstrap" # 검수 컨슈머와 같은 이미지
    essential = true

    # 이미지 CMD는 검수 컨슈머(app.main:app)다. 모듈 경로만 바꿔 덮는다.
    # `$PORT`는 HCL 보간이 아니다 — HCL은 `$` 다음에 중괄호가 와야 보간하고, 여기선
    # 컨테이너 안의 sh가 펼친다.
    command = ["sh", "-c", "exec uvicorn app.draft_api.app:app --host 0.0.0.0 --port $PORT"]

    portMappings = [{ containerPort = var.ai_draft_port, protocol = "tcp" }]

    environment = [
      { name = "PORT", value = tostring(var.ai_draft_port) },
      # 비용 kill switch는 BE가 아니라 돈이 나가는 이쪽에 둔다. 기본 꺼짐이고, 켜는 순서는
      # 이쪽을 먼저 켠 뒤 seller-api의 PRODUCT_DRAFT_AUTOFILL_ENABLED다.
      { name = "AECHAK_AI_ENABLE_DRAFT", value = tostring(var.ai_draft_enabled) },
      { name = "LANGSMITH_TRACING", value = "false" },
      { name = "LANGCHAIN_TRACING_V2", value = "false" },
    ]

    # 카프카 주소를 **주지 않는다.** 이 앱의 lifespan이 컨슈머를 아예 만들지 않는 것이
    # 설계이고, 주소가 없으면 설정 사고로도 컨슈머가 될 수 없다.
    secrets = [
      {
        name      = "AECHAK_AI_ANTHROPIC_API_KEY"
        valueFrom = "arn:aws:ssm:${var.region}:${data.aws_caller_identity.me.account_id}:parameter/${var.project}/${var.env}/ai/AECHAK_AI_ANTHROPIC_API_KEY"
      },
      # 비면 앱이 전건 403으로 닫는다. 그래서 미등재 상태로 태스크가 뜨는 것보다
      # 기동 자체가 실패하는 쪽이 낫다 — secrets 참조가 그 실패를 만든다.
      {
        name      = "AECHAK_AI_DRAFT_SHARED_SECRET"
        valueFrom = "arn:aws:ssm:${var.region}:${data.aws_caller_identity.me.account_id}:parameter/${var.project}/${var.env}/ai/AECHAK_AI_DRAFT_SHARED_SECRET"
      },
    ]

    logConfiguration = {
      logDriver = "awslogs"
      options = {
        awslogs-group         = aws_cloudwatch_log_group.ai_draft.name
        awslogs-region        = var.region
        awslogs-stream-prefix = "ai-draft"
      }
    }
  }])
}

resource "aws_ecs_service" "ai_draft" {
  name            = "${var.project}-ai-draft-${var.env}"
  cluster         = aws_ecs_cluster.main.id
  task_definition = aws_ecs_task_definition.ai_draft.arn
  desired_count   = var.ai_draft_desired_count # 검수 서비스와 같은 근거로 기본 0
  launch_type     = "FARGATE"

  deployment_circuit_breaker {
    enable   = true
    rollback = true
  }

  network_configuration {
    subnets          = [aws_subnet.app_a.id]
    security_groups  = [aws_security_group.ai_draft.id]
    assign_public_ip = false
  }

  service_registries {
    registry_arn = aws_service_discovery_service.ai_draft.arn
  }

  lifecycle {
    ignore_changes = [task_definition] # CI가 새 리비전 등록 — TF가 되돌리지 않게
  }
}
