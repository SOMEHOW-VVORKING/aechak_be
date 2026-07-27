# 존은 콘솔에서 만들었고 가비아 NS 위임이 걸려 있다. data로만 읽는다 —
# resource로 소유하면 destroy 한 번에 도메인 전체가 끊긴다.
data "aws_route53_zone" "root" {
  name = "aechak.co.kr."
}

locals {
  # 콘솔 발급, 갱신은 ACM 관리형(리소스에 붙어 있는 동안만 자동 갱신된다).
  alb_cert_arn = "arn:aws:acm:ap-northeast-2:334455667515:certificate/2f2eeeca-3151-42a5-a35d-3fb403e0ba54"

  # 서브도메인이 평평한 이유: 인증서 SAN이 aechak.co.kr + *.aechak.co.kr 뿐이고
  # 와일드카드는 한 레이블만 매칭한다. api.dev.aechak.co.kr은 이 인증서로 못 덮는다.
  # 규칙은 {서비스}-{env}, prod만 env 생략.
  api_domain = "api-${var.env}.aechak.co.kr"
}

resource "aws_route53_record" "api" {
  zone_id = data.aws_route53_zone.root.zone_id
  name    = local.api_domain
  type    = "A"

  alias {
    name    = aws_lb.main.dns_name
    zone_id = aws_lb.main.zone_id

    # 단일 ALB라 페일오버 대상이 없다 — 헬스 평가로 얻는 게 없음
    evaluate_target_health = false
  }
}
