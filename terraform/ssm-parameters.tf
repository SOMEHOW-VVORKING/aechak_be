# 앱이 부팅 시 /aechak/{env}/api/ 아래를 통째로 읽어 yml의 플레이스홀더로 매핑한다.
# taskdef에 값을 박으면 엔드포인트가 바뀔 때마다 리비전 재배포가 필요해져서 SSM을 거친다.
#
# 접속 정보 7개는 기본값이 없어 하나라도 빠지면 앱이 기동에 실패한다.
# 아래 웹 연동 3개는 기본값이 있어 없어도 뜨지만, 없으면 CORS와 소셜 로그인이 동작하지 않는다.
# 카카오 키·JWT 키는 여기 없다 — terraform이 값을 알 수 없다(없어도 앱은 뜬다).

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
