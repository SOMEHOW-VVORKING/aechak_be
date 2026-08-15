# 문의 접수 통지 메일 발송용 SES.
# env별 메일 서브도메인으로 도메인 아이덴티티를 잡아 dev/prod가 한 계정에서도 충돌하지 않게 한다.
# 지역은 앱과 동일한 기본 provider(ap-northeast-2). 검증 후 no-reply@<메일도메인>으로 발송.
# 신규 계정은 SES 샌드박스라, 검증 안 된 임의 수신자에게 보내려면 콘솔에서 프로덕션 액세스를 별도 신청해야 한다.

locals {
  mail_domain      = var.env == "prod" ? "mail.aechak.co.kr" : "mail-${var.env}.aechak.co.kr"
  ses_from_address = "no-reply@${local.mail_domain}"
}

resource "aws_ses_domain_identity" "mail" {
  domain = local.mail_domain
}

# 도메인 소유 검증 TXT
resource "aws_route53_record" "ses_verification" {
  zone_id = data.aws_route53_zone.root.zone_id
  name    = "_amazonses.${local.mail_domain}"
  type    = "TXT"
  ttl     = 600
  records = [aws_ses_domain_identity.mail.verification_token]
}

resource "aws_ses_domain_dkim" "mail" {
  domain = aws_ses_domain_identity.mail.domain
}

# DKIM 서명 검증 CNAME 3개
resource "aws_route53_record" "ses_dkim" {
  count   = 3
  zone_id = data.aws_route53_zone.root.zone_id
  name    = "${aws_ses_domain_dkim.mail.dkim_tokens[count.index]}._domainkey.${local.mail_domain}"
  type    = "CNAME"
  ttl     = 600
  records = ["${aws_ses_domain_dkim.mail.dkim_tokens[count.index]}.dkim.amazonses.com"]
}

# TXT 전파 후 도메인 검증 완료까지 apply를 막는다 (검증 전 배포로 SendEmail이 거부되는 걸 방지)
resource "aws_ses_domain_identity_verification" "mail" {
  domain     = aws_ses_domain_identity.mail.id
  depends_on = [aws_route53_record.ses_verification]
}
