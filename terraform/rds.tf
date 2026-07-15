resource "aws_db_subnet_group" "main" {
  name       = "${var.project}-${var.env}"
  subnet_ids = [aws_subnet.data_a.id, aws_subnet.data_c.id] # 2-AZ 요건
}

# 한글 서비스 — utf8mb4 강제
resource "aws_db_parameter_group" "mysql" {
  name_prefix = "${var.project}-mysql84-"
  family      = "mysql8.4"

  parameter {
    name  = "character_set_server"
    value = "utf8mb4"
  }

  parameter {
    name  = "collation_server"
    value = "utf8mb4_0900_ai_ci"
  }

  parameter {
    name  = "time_zone"
    value = "Asia/Seoul"
  }

  lifecycle { create_before_destroy = true }
}

resource "random_password" "db" {
  length  = 24
  special = false
}

resource "aws_db_instance" "main" {
  identifier     = "${var.project}-${var.env}"
  engine         = "mysql"
  engine_version = "8.4"
  instance_class = var.db_instance_class

  db_name  = var.project
  username = "app"
  password = random_password.db.result

  allocated_storage     = 20
  max_allocated_storage = 50
  storage_type          = "gp3"

  db_subnet_group_name   = aws_db_subnet_group.main.name
  vpc_security_group_ids = [aws_security_group.mysql.id]
  parameter_group_name   = aws_db_parameter_group.mysql.name

  # 저장구간 암호화 — 기존 인스턴스엔 나중에 못 켠다(재생성만 가능), 지금 켜야 비용 0
  storage_encrypted = true

  multi_az                  = false # MVP: Single-AZ
  skip_final_snapshot       = var.env != "prod"
  final_snapshot_identifier = "${var.project}-${var.env}-final"
  deletion_protection       = var.env == "prod"
  backup_retention_period   = 7

  tags = { Name = "${var.project}-${var.env}" }
}

# 앱이 부팅 시 직접 읽는 설정 — 앱이 부팅 시 import로 읽어 yml의 ${SPRING_DATASOURCE_*} 플레이스홀더로 매핑
resource "aws_ssm_parameter" "db_url" {
  name  = "/${var.project}/${var.env}/api/SPRING_DATASOURCE_URL"
  type  = "String"
  value = "jdbc:mysql://${aws_db_instance.main.address}:3306/${var.project}"
}

resource "aws_ssm_parameter" "db_username" {
  name  = "/${var.project}/${var.env}/api/SPRING_DATASOURCE_USERNAME"
  type  = "String"
  value = "app"
}

resource "aws_ssm_parameter" "db_password" {
  name  = "/${var.project}/${var.env}/api/SPRING_DATASOURCE_PASSWORD"
  type  = "SecureString"
  value = random_password.db.result
}
