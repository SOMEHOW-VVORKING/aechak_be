# SG 매트릭스 — 소스는 CIDR이 아닌 SG 참조. SSH(22) 없음: 접속은 SSM Session Manager.
# sg-alb(80/443 ← 인터넷) → sg-app(8080 ← alb) → mysql/redis/os/kafka(← app)

resource "aws_security_group" "alb" {
  name_prefix = "${var.project}-alb-"
  vpc_id      = aws_vpc.main.id

  ingress {
    from_port   = 80
    to_port     = 80
    protocol    = "tcp"
    cidr_blocks = ["0.0.0.0/0"]
  }

  # 443 리스너보다 먼저(또는 같은 apply에) 열려야 한다. 리스너만 먼저 붙으면
  # 연결이 에러 없이 타임아웃만 나서 원인 찾는 데 시간이 든다.
  ingress {
    from_port   = 443
    to_port     = 443
    protocol    = "tcp"
    cidr_blocks = ["0.0.0.0/0"]
  }

  egress {
    from_port   = 0
    to_port     = 0
    protocol    = "-1"
    cidr_blocks = ["0.0.0.0/0"]
  }

  lifecycle { create_before_destroy = true }
}

resource "aws_security_group" "app" {
  name_prefix = "${var.project}-app-"
  vpc_id      = aws_vpc.main.id

  ingress {
    from_port       = var.app_port
    to_port         = var.app_port
    protocol        = "tcp"
    security_groups = [aws_security_group.alb.id]
  }

  # Toss PG 등 외부 API 호출 + ECR/패키지 pull
  egress {
    from_port   = 0
    to_port     = 0
    protocol    = "-1"
    cidr_blocks = ["0.0.0.0/0"]
  }

  lifecycle { create_before_destroy = true }
}

resource "aws_security_group" "mysql" {
  name_prefix = "${var.project}-mysql-"
  vpc_id      = aws_vpc.main.id

  ingress {
    from_port       = 3306
    to_port         = 3306
    protocol        = "tcp"
    security_groups = [aws_security_group.app.id]
  }

  # 개발자 DB 접속(DataGrip 등)용 — 전용 bastion을 SSM 점프호스트로 쓰는 터널의 마지막 홉.
  # 인바운드 포트는 여전히 0개(SSM은 아웃바운드 기반), 관리 경로만 추가 (bastion.tf 참조)
  ingress {
    from_port       = 3306
    to_port         = 3306
    protocol        = "tcp"
    security_groups = [aws_security_group.bastion.id]
  }

  lifecycle { create_before_destroy = true }
}

resource "aws_security_group" "kafka" {
  name_prefix = "${var.project}-kafka-"
  vpc_id      = aws_vpc.main.id

  ingress {
    from_port       = 9092
    to_port         = 9092
    protocol        = "tcp"
    security_groups = [aws_security_group.app.id]
  }

  # docker pull 등
  egress {
    from_port   = 0
    to_port     = 0
    protocol    = "-1"
    cidr_blocks = ["0.0.0.0/0"]
  }

  lifecycle { create_before_destroy = true }
}

resource "aws_security_group" "redis" {
  name_prefix = "${var.project}-redis-"
  vpc_id      = aws_vpc.main.id

  ingress {
    from_port       = 6379
    to_port         = 6379
    protocol        = "tcp"
    security_groups = [aws_security_group.app.id]
  }

  lifecycle { create_before_destroy = true }
}

# ── 도입 시점에 주석 해제 ──
# resource "aws_security_group" "opensearch" {
#   name_prefix = "${var.project}-os-"
#   vpc_id      = aws_vpc.main.id
#   ingress {
#     from_port       = 443
#     to_port         = 443
#     protocol        = "tcp"
#     security_groups = [aws_security_group.app.id]
#   }
# }

# AI 검수 서버 전용 SG. app SG를 재사용하지 않는 이유는 그 SG가 ALB에서 8080을 받고
# MySQL·Redis·Kafka 모두에 닿는 신뢰 수준이기 때문이다. 검수 서버는 DB를 모르는 것이
# 설계 전제이고, 그 전제를 코드가 아니라 보안그룹으로 집행한다.
resource "aws_security_group" "ai" {
  name_prefix = "${var.project}-ai-"
  vpc_id      = aws_vpc.main.id

  ingress {
    from_port       = var.ai_app_port
    to_port         = var.ai_app_port
    protocol        = "tcp"
    security_groups = [aws_security_group.alb.id]
  }

  # 외부 모델 API 호출 + ECR·CloudWatch. NAT를 지난다.
  egress {
    from_port   = 0
    to_port     = 0
    protocol    = "-1"
    cidr_blocks = ["0.0.0.0/0"]
  }

  lifecycle { create_before_destroy = true }
}

# Kafka에 AI 서버를 들인다. 검수 요청과 판정이 오가는 유일한 통로다.
# MySQL·Redis 쪽에는 같은 규칙을 넣지 않는다 — 넣으면 '판정기는 DB를 모른다'가 깨진다.
resource "aws_security_group_rule" "kafka_from_ai" {
  type                     = "ingress"
  from_port                = 9092
  to_port                  = 9092
  protocol                 = "tcp"
  security_group_id        = aws_security_group.kafka.id
  source_security_group_id = aws_security_group.ai.id
  description              = "ai inspection service" # AWS가 설명에 ASCII만 허용한다
}

# 초안 API 전용 SG. 공개 ALB가 아니라 **BE 앱 SG에서만** 받는다 — 그 한 줄이
# `/internal/seller-draft`(호출마다 모델 비용)를 인터넷에서 떼어 놓는 첫째 겹이고,
# 공유 비밀 헤더가 둘째 겹이다. 검수 서버 SG와도 분리한다: 저쪽은 ALB에서 받고
# 카프카에 닿지만 초안은 둘 다 필요 없다.
resource "aws_security_group" "ai_draft" {
  name_prefix = "${var.project}-ai-draft-"
  vpc_id      = aws_vpc.main.id

  ingress {
    from_port       = var.ai_draft_port
    to_port         = var.ai_draft_port
    protocol        = "tcp"
    security_groups = [aws_security_group.app.id]
  }

  # 외부 모델 API 호출 + ECR·CloudWatch. NAT를 지난다.
  egress {
    from_port   = 0
    to_port     = 0
    protocol    = "-1"
    cidr_blocks = ["0.0.0.0/0"]
  }

  lifecycle { create_before_destroy = true }
}

