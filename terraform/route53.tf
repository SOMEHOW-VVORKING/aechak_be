# 존은 콘솔에서 만들었고 가비아 NS 위임이 걸려 있다. data로만 읽는다 —
# resource로 소유하면 destroy 한 번에 도메인 전체가 끊긴다.
data "aws_route53_zone" "root" {
  name = "aechak.co.kr."

  # 같은 이름의 private zone이 생기면 조회 결과가 둘이 되어 plan 전체가 실패한다
  private_zone = false
}

locals {
  # 콘솔 발급, 갱신은 ACM 관리형(리소스에 붙어 있는 동안만 자동 갱신된다).
  # CloudFront는 us-east-1 인증서만 받음.
  alb_cert_arn = "arn:aws:acm:ap-northeast-2:334455667515:certificate/2f2eeeca-3151-42a5-a35d-3fb403e0ba54"
  cf_cert_arn  = "arn:aws:acm:us-east-1:334455667515:certificate/a798e84c-ef83-4273-bce8-9e55d70849dc"

  # 서브도메인이 평평한 이유: 인증서 SAN이 aechak.co.kr + *.aechak.co.kr 뿐이고
  # 와일드카드는 한 레이블만 매칭한다. api.dev.aechak.co.kr은 이 인증서로 못 덮는다.
  # prod만 env를 뺀다 — 사용자에게 노출되는 이름이라. 이것도 같은 인증서가 덮는다.
  api_domain   = var.env == "prod" ? "api.aechak.co.kr" : "api-${var.env}.aechak.co.kr"
  media_domain = var.env == "prod" ? "media.aechak.co.kr" : "media-${var.env}.aechak.co.kr"
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

# cloudfront.tf의 aliases와 짝이다. 배포에 호스트명이 등록돼 있지 않으면
# CloudFront가 Host 헤더로 배포를 못 찾아 이 레코드만으로는 응답하지 않는다.
resource "aws_route53_record" "media" {
  zone_id = data.aws_route53_zone.root.zone_id
  name    = local.media_domain
  type    = "A"

  alias {
    name                   = aws_cloudfront_distribution.media.domain_name
    zone_id                = aws_cloudfront_distribution.media.hosted_zone_id
    evaluate_target_health = false
  }
}
