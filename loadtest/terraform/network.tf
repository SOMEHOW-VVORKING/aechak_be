# 팀 VPC와 서브넷은 읽기만 한다
data "aws_vpc" "team" {
  tags = { Name = "aechak-dev" }
}

data "aws_subnet" "public_a" {
  vpc_id = data.aws_vpc.team.id
  tags   = { Name = "aechak-public-a" }
}

data "aws_subnet" "data_a" {
  vpc_id = data.aws_vpc.team.id
  tags   = { Name = "aechak-data-a" }
}

data "aws_subnet" "data_c" {
  vpc_id = data.aws_vpc.team.id
  tags   = { Name = "aechak-data-c" }
}

resource "aws_security_group" "runner" {
  name        = "aechak-perf-runner"
  description = "perf runner, no ingress"
  vpc_id      = data.aws_vpc.team.id

  egress {
    from_port   = 0
    to_port     = 0
    protocol    = "-1"
    cidr_blocks = ["0.0.0.0/0"]
  }

  tags = { Name = "aechak-perf-runner" }
}

resource "aws_security_group" "api" {
  name        = "aechak-perf-api"
  description = "perf api host, from runner only"
  vpc_id      = data.aws_vpc.team.id

  dynamic "ingress" {
    for_each = { api = 8080, management = 8081, node_exporter = 9100, cadvisor = 8088 }
    content {
      description     = ingress.key
      from_port       = ingress.value
      to_port         = ingress.value
      protocol        = "tcp"
      security_groups = [aws_security_group.runner.id]
    }
  }

  egress {
    from_port   = 0
    to_port     = 0
    protocol    = "-1"
    cidr_blocks = ["0.0.0.0/0"]
  }

  tags = { Name = "aechak-perf-api" }
}

resource "aws_security_group" "es" {
  name        = "aechak-perf-es"
  description = "perf es host, from api and runner only"
  vpc_id      = data.aws_vpc.team.id

  ingress {
    description     = "elasticsearch"
    from_port       = 9200
    to_port         = 9200
    protocol        = "tcp"
    security_groups = [aws_security_group.api.id, aws_security_group.runner.id]
  }

  dynamic "ingress" {
    for_each = { es_exporter = 9114, node_exporter = 9100 }
    content {
      description     = ingress.key
      from_port       = ingress.value
      to_port         = ingress.value
      protocol        = "tcp"
      security_groups = [aws_security_group.runner.id]
    }
  }

  egress {
    from_port   = 0
    to_port     = 0
    protocol    = "-1"
    cidr_blocks = ["0.0.0.0/0"]
  }

  tags = { Name = "aechak-perf-es" }
}

resource "aws_security_group" "rds" {
  name        = "aechak-perf-rds"
  description = "perf rds, from api and runner only"
  vpc_id      = data.aws_vpc.team.id

  ingress {
    description     = "mysql"
    from_port       = 3306
    to_port         = 3306
    protocol        = "tcp"
    security_groups = [aws_security_group.api.id, aws_security_group.runner.id]
  }

  tags = { Name = "aechak-perf-rds" }
}
