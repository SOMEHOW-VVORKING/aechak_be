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
  alb_cert_arn = "arn:aws:acm:ap-northeast-2:447170313132:certificate/666f3585-2b77-4dcf-8558-64b0a7779cbd"
  cf_cert_arn  = "arn:aws:acm:us-east-1:447170313132:certificate/28ef64ca-2022-4fa3-b409-3b8901e2d2b9"

  # 서브도메인이 평평한 이유: 인증서 SAN이 aechak.co.kr + *.aechak.co.kr 뿐이고
  # 와일드카드는 한 레이블만 매칭한다. api.dev.aechak.co.kr은 이 인증서로 못 덮는다.
  # prod만 env를 뺀다 — 사용자에게 노출되는 이름이라. 이것도 같은 인증서가 덮는다.
  api_domain   = var.env == "prod" ? "api.aechak.co.kr" : "api-${var.env}.aechak.co.kr"
  media_domain = var.env == "prod" ? "media.aechak.co.kr" : "media-${var.env}.aechak.co.kr"

  # 프론트는 서비스 얼굴이라 서비스명을 빼고 env만 쓴다.
  # prod는 apex가 되는데 CNAME을 못 걸어서 그때 방식을 다시 정해야 한다.
  web_domain = var.env == "prod" ? "aechak.co.kr" : "${var.env}.aechak.co.kr"

  # 셀러센터·어드민 콘솔 프론트 도메인. 콘솔이라 서비스명을 남긴다(prod도 서브도메인).
  seller_domain = var.env == "prod" ? "seller.aechak.co.kr" : "seller-${var.env}.aechak.co.kr"
  admin_domain  = var.env == "prod" ? "admin.aechak.co.kr" : "admin-${var.env}.aechak.co.kr"
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

# Cloudflare Pages는 대시보드 등록이 선행돼야 이 레코드가 동작한다.
# 별칭이 아닌 CNAME인 이유는 대상이 AWS 리소스가 아니라서.
resource "aws_route53_record" "web" {
  zone_id = data.aws_route53_zone.root.zone_id
  name    = local.web_domain
  type    = "CNAME"
  ttl     = 300
  records = ["aechak-fe.pages.dev"]
}

# web과 같은 Cloudflare Pages CNAME. Pages 프로젝트에 커스텀 도메인 등록이 선행돼야 검증된다.
resource "aws_route53_record" "seller_web" {
  zone_id = data.aws_route53_zone.root.zone_id
  name    = local.seller_domain
  type    = "CNAME"
  ttl     = 300
  records = ["aechak-seller.pages.dev"]
}

resource "aws_route53_record" "admin_web" {
  zone_id = data.aws_route53_zone.root.zone_id
  name    = local.admin_domain
  type    = "CNAME"
  ttl     = 300
  records = ["aechak-admin.pages.dev"]
}
