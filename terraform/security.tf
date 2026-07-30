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
