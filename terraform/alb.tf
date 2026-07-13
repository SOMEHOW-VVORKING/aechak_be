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

  default_action {
    type             = "forward"
    target_group_arn = aws_lb_target_group.app.arn
  }
}

# TODO: 도메인 확정 시 — ACM 인증서 + 443 리스너 + 80은 redirect로 전환
