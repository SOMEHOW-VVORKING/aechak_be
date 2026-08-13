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
