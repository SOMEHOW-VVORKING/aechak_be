resource "aws_lb" "main" {
  name               = "${var.project}-${var.env}"
  load_balancer_type = "application"
  security_groups    = [aws_security_group.alb.id]
  subnets            = [aws_subnet.public_a.id, aws_subnet.public_c.id]
}

resource "aws_lb_target_group" "app" {
  name_prefix = "aechak" # 교체 시 이름충돌 방지 (max 6자)
  port        = var.app_port
  protocol    = "HTTP"
  vpc_id      = aws_vpc.main.id
  target_type = "ip" # Fargate(awsvpc)는 인스턴스가 아니라 태스크 IP를 타겟으로

  lifecycle { create_before_destroy = true }

  health_check {
    path                = "/actuator/health"
    interval            = 30
    healthy_threshold   = 2
    unhealthy_threshold = 3
  }
}

resource "aws_lb_listener" "http" {
  load_balancer_arn = aws_lb.main.arn
  port              = 80
  protocol          = "HTTP"

  # host·path·query는 생략하면 원래 값이 유지된다 (/actuator/health가 그대로 따라감)
  default_action {
    type = "redirect"

    redirect {
      port        = "443"
      protocol    = "HTTPS"
      status_code = "HTTP_301"
    }
  }
}

# ssl_policy를 생략하면 TLS 1.0까지 받는 2016-08이 붙는다. Res는 CBC 계열을 뺀 것
resource "aws_lb_listener" "https" {
  load_balancer_arn = aws_lb.main.arn
  port              = 443
  protocol          = "HTTPS"
  ssl_policy        = "ELBSecurityPolicy-TLS13-1-2-Res-PQ-2025-09"
  certificate_arn   = local.alb_cert_arn

  default_action {
    type             = "forward"
    target_group_arn = aws_lb_target_group.app.arn
  }
}

# ── seller-api (SCRUM-193): host 기반 분기 ─────────────
resource "aws_lb_target_group" "seller_api" {
  name_prefix = "seller" # 교체 시 이름충돌 방지 (max 6자)
  port        = var.app_port
  protocol    = "HTTP"
  vpc_id      = aws_vpc.main.id
  target_type = "ip"

  lifecycle { create_before_destroy = true }

  health_check {
    path                = "/actuator/health"
    interval            = 30
    healthy_threshold   = 2
    unhealthy_threshold = 3
  }
}

# 인증서 추가 불필요 — 기존 SAN(aechak.co.kr + *.aechak.co.kr)의 와일드카드가
# seller-api-<env>.aechak.co.kr(한 레이블)을 덮는다.
resource "aws_lb_listener_rule" "seller_api" {
  listener_arn = aws_lb_listener.https.arn
  priority     = 10 # 명시 규칙이 이것뿐이라 값은 상징적 — 규칙이 늘면 대역을 설계한다

  condition {
    host_header {
      values = [local.seller_api_domain]
    }
  }

  action {
    type             = "forward"
    target_group_arn = aws_lb_target_group.seller_api.arn
  }
}
