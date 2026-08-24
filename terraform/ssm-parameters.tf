# 앱이 부팅 시 /aechak/{env}/api/ 아래를 통째로 읽어 yml의 플레이스홀더로 매핑한다.
# taskdef에 값을 박으면 엔드포인트가 바뀔 때마다 리비전 재배포가 필요해져서 SSM을 거친다.
#
# 접속 정보 7개는 기본값이 없어 하나라도 빠지면 앱이 기동에 실패한다.
# 아래 웹 연동 3개는 기본값이 있어 없어도 뜨지만, 없으면 CORS와 소셜 로그인이 동작하지 않는다.
# 카카오 키·JWT 키·CoolSMS 키(COOLSMS_API_KEY·COOLSMS_API_SECRET·COOLSMS_FROM)는 여기 없다 —
# terraform이 값을 알 수 없어 수기 등재한다. 카카오·JWT는 없어도 앱이 뜨지만 COOLSMS_* 3개는 없으면 부팅 실패.

resource "aws_ssm_parameter" "db_url" {
  name  = "/${var.project}/${var.env}/api/SPRING_DATASOURCE_URL"
  type  = "String"
  value = "jdbc:mysql://${aws_db_instance.main.address}:3306/${var.project}"
}

# 사용자명을 리터럴로 또 적으면 rds.tf와 어긋날 수 있다 — 설정된 값을 그대로 되읽는다
resource "aws_ssm_parameter" "db_username" {
  name  = "/${var.project}/${var.env}/api/SPRING_DATASOURCE_USERNAME"
  type  = "String"
  value = aws_db_instance.main.username
}

resource "aws_ssm_parameter" "db_password" {
  name  = "/${var.project}/${var.env}/api/SPRING_DATASOURCE_PASSWORD"
  type  = "SecureString"
  value = random_password.db.result
}

resource "aws_ssm_parameter" "redis_host" {
  name  = "/${var.project}/${var.env}/api/SPRING_DATA_REDIS_HOST"
  type  = "String"
  value = aws_elasticache_cluster.redis.cache_nodes[0].address
}

resource "aws_ssm_parameter" "redis_port" {
  name  = "/${var.project}/${var.env}/api/SPRING_DATA_REDIS_PORT"
  type  = "String"
  value = "6379"
}

resource "aws_ssm_parameter" "media_bucket" {
  name  = "/${var.project}/${var.env}/api/AWS_S3_MEDIA_BUCKET"
  type  = "String"
  value = aws_s3_bucket.media.id
}

resource "aws_ssm_parameter" "docs_bucket" {
  name  = "/${var.project}/${var.env}/api/AWS_S3_DOCS_BUCKET"
  type  = "String"
  value = aws_s3_bucket.docs.id
}

# 저장은 key, 응답은 URL이라 앱이 표시용 URL을 조립할 때 쓴다.
# 없으면 부팅·API는 정상이고 이미지 URL만 도메인 없는 상대경로로 나간다.
resource "aws_ssm_parameter" "media_public_base_url" {
  name  = "/${var.project}/${var.env}/api/AWS_S3_MEDIA_PUBLIC_BASE_URL"
  type  = "String"
  value = "https://${local.media_domain}"
}

# ── 웹 연동 ─────────────────────────────────────────────
# 배포된 프론트 + 로컬 개발 프론트. 로컬에서 dev 백엔드에 붙는 구성을 함께 허용한다.
locals {
  web_origins = concat(["https://${local.web_domain}"], var.frontend_origins)
}

resource "aws_ssm_parameter" "cors_origins" {
  name  = "/${var.project}/${var.env}/api/CORS_ALLOWED_ORIGINS"
  type  = "String"
  value = join(",", local.web_origins)
}

# provider 콘솔에 등록한 주소와 정확히 같아야 한다 — 다르면 카카오가 리다이렉트를 거부한다
resource "aws_ssm_parameter" "auth_callback_base_url" {
  name  = "/${var.project}/${var.env}/api/AUTH_WEB_CALLBACK_BASE_URL"
  type  = "String"
  value = "https://${local.api_domain}/api/v1/auth/callback"
}

# 정확 일치 화이트리스트 — 오픈 리다이렉트 방지라 여기 없는 주소로는 복귀가 거부된다
resource "aws_ssm_parameter" "auth_return_urls" {
  name  = "/${var.project}/${var.env}/api/AUTH_WEB_RETURN_URLS"
  type  = "String"
  value = join(",", [for o in local.web_origins : "${o}/login/complete"])
}

# ── 문의 통지(SES) ─────────────────────────────────────
# enabled=true인데 from/recipients가 비면 앱이 fail-fast로 기동에 실패한다 — 셋을 함께 넣는다.
resource "aws_ssm_parameter" "ses_from" {
  name  = "/${var.project}/${var.env}/api/AWS_SES_FROM"
  type  = "String"
  value = local.ses_from_address
}

resource "aws_ssm_parameter" "inquiry_ops_recipients" {
  name  = "/${var.project}/${var.env}/api/INQUIRY_OPS_RECIPIENTS"
  type  = "String"
  value = join(",", var.inquiry_ops_recipients)
}

resource "aws_ssm_parameter" "inquiry_notification_enabled" {
  name  = "/${var.project}/${var.env}/api/INQUIRY_NOTIFICATION_ENABLED"
  type  = "String"
  value = tostring(var.inquiry_notification_enabled)
}

# --- PII 암호화 키 (SCRUM-169) ---
# 앱이 전화번호 등 민감정보를 암호화(AES-GCM)·검색 해시(HMAC)하는 데 쓴다. 키 분실 = 해당 데이터 영구 복호 불능이라
# 사람 손을 거치지 않고 terraform이 생성·등재한다(수기 오등재 방지 — db_password와 같은 결).
# 키 로테이션은 상시 기능이 아니라 계획된 이벤트다: v2 리소스를 추가하고 앱의 active-version을 올린다(v1은 복호용으로 유지).

resource "random_bytes" "pii_aes_key_v1" {
  length = 32

  lifecycle {
    prevent_destroy = true # 재생성 = 기존 PII 영구 복호 불능. 철거는 이 가드를 손수 제거한 뒤에만
  }
}

resource "random_bytes" "pii_hmac_key" {
  length = 32

  lifecycle {
    prevent_destroy = true # 재생성 = 기존 blind index 전부 고아. 철거는 이 가드를 손수 제거한 뒤에만
  }
}

resource "aws_ssm_parameter" "pii_aes_key_v1" {
  name  = "/${var.project}/${var.env}/api/PII_AES_KEY_V1"
  type  = "SecureString"
  value = random_bytes.pii_aes_key_v1.base64

  lifecycle {
    prevent_destroy = true # 파라미터 삭제 = 앱 부팅 fail-fast. 철거는 이 가드를 손수 제거한 뒤에만
  }
}

# 검색용 해시 키 — 입력 공간이 좁아(전화번호) 키·DB 동시 유출 시 전수 역산이 가능한 평문 등가 시크릿. AES 키와 분리한다
resource "aws_ssm_parameter" "pii_hmac_key" {
  name  = "/${var.project}/${var.env}/api/PII_HMAC_KEY"
  type  = "SecureString"
  value = random_bytes.pii_hmac_key.base64

  lifecycle {
    prevent_destroy = true # 파라미터 삭제 = 앱 부팅 fail-fast. 철거는 이 가드를 손수 제거한 뒤에만
  }
}

# ── seller-api (SCRUM-193): /aechak/{env}/seller/ ──────
# 값은 api와 같은 원천(tf 리소스)을 재참조한다 — 두 경로가 항상 같은 값이 되도록 원천을 하나로 둔다.
# 별도 경로인 이유: 기존 /api/ 경로는 prevent_destroy 가드(PII)라 이동 불가 + IAM 스코프를 모듈 단위로 유지.

resource "aws_ssm_parameter" "seller_db_url" {
  name  = "/${var.project}/${var.env}/seller/SPRING_DATASOURCE_URL"
  type  = "String"
  value = "jdbc:mysql://${aws_db_instance.main.address}:3306/${var.project}"
}

resource "aws_ssm_parameter" "seller_db_username" {
  name  = "/${var.project}/${var.env}/seller/SPRING_DATASOURCE_USERNAME"
  type  = "String"
  value = aws_db_instance.main.username
}

resource "aws_ssm_parameter" "seller_db_password" {
  name  = "/${var.project}/${var.env}/seller/SPRING_DATASOURCE_PASSWORD"
  type  = "SecureString"
  value = random_password.db.result
}

resource "aws_ssm_parameter" "seller_redis_host" {
  name  = "/${var.project}/${var.env}/seller/SPRING_DATA_REDIS_HOST"
  type  = "String"
  value = aws_elasticache_cluster.redis.cache_nodes[0].address
}

resource "aws_ssm_parameter" "seller_redis_port" {
  name  = "/${var.project}/${var.env}/seller/SPRING_DATA_REDIS_PORT"
  type  = "String"
  value = "6379"
}

resource "aws_ssm_parameter" "seller_media_bucket" {
  name  = "/${var.project}/${var.env}/seller/AWS_S3_MEDIA_BUCKET"
  type  = "String"
  value = aws_s3_bucket.media.id
}

resource "aws_ssm_parameter" "seller_docs_bucket" {
  name  = "/${var.project}/${var.env}/seller/AWS_S3_DOCS_BUCKET"
  type  = "String"
  value = aws_s3_bucket.docs.id
}

resource "aws_ssm_parameter" "seller_media_public_base_url" {
  name  = "/${var.project}/${var.env}/seller/AWS_S3_MEDIA_PUBLIC_BASE_URL"
  type  = "String"
  value = "https://${local.media_domain}"
}

# 셀러센터 웹 오리진 — 구매자 웹과 별개 목록. 배포된 셀러 웹 도메인이 생기면 여기 합류
resource "aws_ssm_parameter" "seller_cors_origins" {
  name  = "/${var.project}/${var.env}/seller/CORS_ALLOWED_ORIGINS"
  type  = "String"
  value = join(",", var.seller_frontend_origins)
}

# PII 키 — api와 같은 random_bytes를 재참조(같은 DB의 같은 암호문을 복호해야 하므로 값이 달라질 수 없는 구조로)
resource "aws_ssm_parameter" "seller_pii_aes_key_v1" {
  name  = "/${var.project}/${var.env}/seller/PII_AES_KEY_V1"
  type  = "SecureString"
  value = random_bytes.pii_aes_key_v1.base64

  lifecycle {
    prevent_destroy = true # 파라미터 삭제 = seller 부팅 fail-fast
  }
}

resource "aws_ssm_parameter" "seller_pii_hmac_key" {
  name  = "/${var.project}/${var.env}/seller/PII_HMAC_KEY"
  type  = "SecureString"
  value = random_bytes.pii_hmac_key.base64

  lifecycle {
    prevent_destroy = true # 파라미터 삭제 = seller 부팅 fail-fast
  }
}

# --- JWT RS256 키: 수기 등재 (헤더의 카카오·JWT 키 방침과 동일) ---
# terraform으로 생성하면 개인키가 state에 남아 여기서는 관리하지 않는다.
# 실행 모듈 간 키 공유가 전제라 아래 파라미터를 사람이 직접 등재한다:
#   /{project}/{env}/api/AUTH_JWT_PRIVATE_KEY  (SecureString, PKCS8 PEM) — api만 발급+검증
#   /{project}/{env}/api/AUTH_JWT_PUBLIC_KEY   (String, PEM)
#   /{project}/{env}/seller/AUTH_JWT_PUBLIC_KEY (String, PEM) — api 공개키와 같은 값이어야 api 발급 토큰을 검증한다
# 누락 시 부팅은 되지만 임시 키 폴백으로 갈라져 모듈 간 검증이 전부 401이 된다.
# 검증 전용 실행 모듈이 늘면(admin 등) 그 모듈 경로에도 공개키를 같은 값으로 등재한다.
