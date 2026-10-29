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
# 앱마다 배포된 프론트 + 로컬 개발 프론트. 로컬에서 dev 백엔드에 붙는 구성을 함께 허용한다.
# api 모듈은 all_web_origins(세 웹 합집합)를, seller·admin 모듈은 자기 콘솔 목록을 쓴다.
locals {
  buyer_origins   = concat(["https://${local.web_domain}"], var.frontend_origins)
  seller_origins  = concat(["https://${local.seller_domain}"], var.seller_frontend_origins)
  admin_origins   = concat(["https://${local.admin_domain}"], var.admin_frontend_origins)
  all_web_origins = concat(local.buyer_origins, local.seller_origins, local.admin_origins)
}

resource "aws_ssm_parameter" "cors_origins" {
  name  = "/${var.project}/${var.env}/api/CORS_ALLOWED_ORIGINS"
  type  = "String"
  value = join(",", local.all_web_origins)
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
  value = join(",", [for o in local.all_web_origins : "${o}/login/complete"])
}

# ── 문의 통지(SES) ─────────────────────────────────────
# enabled=true인데 from/recipients가 비면 앱이 fail-fast로 기동에 실패한다.
resource "aws_ssm_parameter" "ses_from" {
  name  = "/${var.project}/${var.env}/api/AWS_SES_FROM"
  type  = "String"
  value = local.ses_from_address
}

# 수신자 미설정이면 파라미터를 만들지 않는다 — SSM은 빈 값을 거절하고(길이 ≥ 1),
# 앱은 경로 프리픽스 import라 파라미터가 없으면 기본값(빈 목록)으로 부팅한다.
resource "aws_ssm_parameter" "inquiry_ops_recipients" {
  count = length(var.inquiry_ops_recipients) > 0 ? 1 : 0
  name  = "/${var.project}/${var.env}/api/INQUIRY_OPS_RECIPIENTS"
  type  = "String"
  value = join(",", var.inquiry_ops_recipients)
}

resource "aws_ssm_parameter" "inquiry_notification_enabled" {
  name  = "/${var.project}/${var.env}/api/INQUIRY_NOTIFICATION_ENABLED"
  type  = "String"
  value = tostring(var.inquiry_notification_enabled)
}

# --- 포트원 V2 결제 (SCRUM-205) ---
# 넷 다 terraform이 만들지 않는다. 실값을 퍼블릭 레포에 두지 않기로 해 사람이 수기 등재한다.
# 시크릿은 state에 남기지 않는 방침(JWT 키와 같은 결)과도 겹친다.
#   /{project}/{env}/api/PORTONE_STORE_ID       (String)
#   /{project}/{env}/api/PORTONE_CHANNEL_KEY    (String)
#   /{project}/{env}/api/PORTONE_API_SECRET     (SecureString)
#   /{project}/{env}/api/PORTONE_WEBHOOK_SECRET (SecureString)
# 결제는 구매자 api만 호출하므로 seller 경로에는 넣지 않는다.

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

# 셀러센터 웹 오리진 — 구매자 웹과 별개 목록(배포 셀러 도메인 + 로컬 포트)
resource "aws_ssm_parameter" "seller_cors_origins" {
  name  = "/${var.project}/${var.env}/seller/CORS_ALLOWED_ORIGINS"
  type  = "String"
  value = join(",", local.seller_origins)
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

# ── admin (SCRUM-229): /aechak/{env}/admin/ ────────────
# seller와 같은 결 — 값은 api와 같은 원천(tf 리소스)을 재참조하고 경로만 모듈 단위로 분리한다.
# admin은 redis 미사용이라 해당 파라미터가 없다.

resource "aws_ssm_parameter" "admin_db_url" {
  name  = "/${var.project}/${var.env}/admin/SPRING_DATASOURCE_URL"
  type  = "String"
  value = "jdbc:mysql://${aws_db_instance.main.address}:3306/${var.project}"
}

resource "aws_ssm_parameter" "admin_db_username" {
  name  = "/${var.project}/${var.env}/admin/SPRING_DATASOURCE_USERNAME"
  type  = "String"
  value = aws_db_instance.main.username
}

resource "aws_ssm_parameter" "admin_db_password" {
  name  = "/${var.project}/${var.env}/admin/SPRING_DATASOURCE_PASSWORD"
  type  = "SecureString"
  value = random_password.db.result
}

resource "aws_ssm_parameter" "admin_media_bucket" {
  name  = "/${var.project}/${var.env}/admin/AWS_S3_MEDIA_BUCKET"
  type  = "String"
  value = aws_s3_bucket.media.id
}

resource "aws_ssm_parameter" "admin_docs_bucket" {
  name  = "/${var.project}/${var.env}/admin/AWS_S3_DOCS_BUCKET"
  type  = "String"
  value = aws_s3_bucket.docs.id
}

resource "aws_ssm_parameter" "admin_media_public_base_url" {
  name  = "/${var.project}/${var.env}/admin/AWS_S3_MEDIA_PUBLIC_BASE_URL"
  type  = "String"
  value = "https://${local.media_domain}"
}

# 어드민 웹 오리진 — 구매자·셀러 웹과 별개 목록(배포 어드민 도메인 + 로컬 포트)
resource "aws_ssm_parameter" "admin_cors_origins" {
  name  = "/${var.project}/${var.env}/admin/CORS_ALLOWED_ORIGINS"
  type  = "String"
  value = join(",", local.admin_origins)
}

# PII 키 — api·seller와 같은 random_bytes 재참조(같은 DB의 같은 암호문을 복호)
resource "aws_ssm_parameter" "admin_pii_aes_key_v1" {
  name  = "/${var.project}/${var.env}/admin/PII_AES_KEY_V1"
  type  = "SecureString"
  value = random_bytes.pii_aes_key_v1.base64

  lifecycle {
    prevent_destroy = true # 파라미터 삭제 = admin 부팅 fail-fast
  }
}

resource "aws_ssm_parameter" "admin_pii_hmac_key" {
  name  = "/${var.project}/${var.env}/admin/PII_HMAC_KEY"
  type  = "SecureString"
  value = random_bytes.pii_hmac_key.base64

  lifecycle {
    prevent_destroy = true # 파라미터 삭제 = admin 부팅 fail-fast
  }
}

# ── batch (SCRUM-251): /aechak/{env}/batch/ ────────────
# api·seller·admin과 같은 결 — 값은 같은 원천(tf 리소스)을 재참조하고 경로만 모듈 단위로 분리한다.
# batch는 웹 모듈이 아니라 CORS·JWT 공개키가 없다. redis도 쓰지 않는다.

resource "aws_ssm_parameter" "batch_db_url" {
  name  = "/${var.project}/${var.env}/batch/SPRING_DATASOURCE_URL"
  type  = "String"
  value = "jdbc:mysql://${aws_db_instance.main.address}:3306/${var.project}"
}

resource "aws_ssm_parameter" "batch_db_username" {
  name  = "/${var.project}/${var.env}/batch/SPRING_DATASOURCE_USERNAME"
  type  = "String"
  value = aws_db_instance.main.username
}

resource "aws_ssm_parameter" "batch_db_password" {
  name  = "/${var.project}/${var.env}/batch/SPRING_DATASOURCE_PASSWORD"
  type  = "SecureString"
  value = random_password.db.result
}

# 아웃박스 재발행이 쓰는 브로커 주소. api 경로 파라미터(ec2.tf)와 같은 값을 재참조한다
resource "aws_ssm_parameter" "batch_kafka_bootstrap" {
  name  = "/${var.project}/${var.env}/batch/SPRING_KAFKA_BOOTSTRAP_SERVERS"
  type  = "String"
  value = aws_ssm_parameter.kafka_bootstrap.value
}

# 배치는 파일을 다루지 않지만 주문·장바구니 파사드가 FileStorage 조립을 요구해 버킷명이 비면 안 된다
resource "aws_ssm_parameter" "batch_media_bucket" {
  name  = "/${var.project}/${var.env}/batch/AWS_S3_MEDIA_BUCKET"
  type  = "String"
  value = aws_s3_bucket.media.id
}

resource "aws_ssm_parameter" "batch_docs_bucket" {
  name  = "/${var.project}/${var.env}/batch/AWS_S3_DOCS_BUCKET"
  type  = "String"
  value = aws_s3_bucket.docs.id
}

resource "aws_ssm_parameter" "batch_media_public_base_url" {
  name  = "/${var.project}/${var.env}/batch/AWS_S3_MEDIA_PUBLIC_BASE_URL"
  type  = "String"
  value = "https://${local.media_domain}"
}

# 대사 조회 대상을 테스트 결제로 한정한다. dev는 테스트 채널이라 true, prod는 false
resource "aws_ssm_parameter" "batch_portone_reconciliation_is_test" {
  name  = "/${var.project}/${var.env}/batch/PORTONE_RECONCILIATION_IS_TEST"
  type  = "String"
  value = tostring(var.env != "prod")
}

# PII 키 — api·seller·admin과 같은 random_bytes 재참조(같은 DB의 같은 암호문을 복호)
resource "aws_ssm_parameter" "batch_pii_aes_key_v1" {
  name  = "/${var.project}/${var.env}/batch/PII_AES_KEY_V1"
  type  = "SecureString"
  value = random_bytes.pii_aes_key_v1.base64

  lifecycle {
    prevent_destroy = true # 파라미터 삭제 = batch 부팅 fail-fast
  }
}

resource "aws_ssm_parameter" "batch_pii_hmac_key" {
  name  = "/${var.project}/${var.env}/batch/PII_HMAC_KEY"
  type  = "SecureString"
  value = random_bytes.pii_hmac_key.base64

  lifecycle {
    prevent_destroy = true # 파라미터 삭제 = batch 부팅 fail-fast
  }
}

# ── batch 스케줄 스위치 (SCRUM-328) ────────────────────
# 일곱 스케줄 모두 코드 기본값이 false다. 의도된 설계다 — 돈을 움직이거나 비가역 전이를 하는
# 경로라서 꺼진 채로 배포하고 환경별로 켠다. 대신 SSM에 값을 넣지 않으면 배치 보트는 떠 있는데
# 스케줄은 하나도 돌지 않는다. 2026-10-29 dev 실측이 정확히 그 상태였다:
# /aechak/dev/batch 에 일곱 개가 전부 없고, CloudWatch 1주일 로그에 일곱 스케줄 전부 0건.
# (대사 스케줄만 플래그 없이 매일 돌지만 그 경로의 포트원 시크릿이 미등재라 FAILED로 끝난다.)
#
# 키 이름은 boot/batch/src/main/resources/application.yml 의 플레이스홀더와 글자까지 같아야 한다.
# 배치는 경로 프리픽스(spring.config.import)로 읽으므로 이름이 틀려도 부팅은 성공하고 플래그만
# 기본값 false로 남는다 — 오타가 조용히 묻힌다. order.delivery.enabled 의 이름이 특히 헷갈리는데
# ORDER_DELIVERY_ENABLED 가 아니라 ORDER_DELIVERY_BATCH_ENABLED 다.
locals {
  batch_schedule_flags = {
    ORDER_EXPIRY_ENABLED             = var.batch_schedules_enabled.order_expiry
    ORDER_AUTO_CONFIRM_ENABLED       = var.batch_schedules_enabled.order_auto_confirm
    ORDER_DELIVERY_BATCH_ENABLED     = var.batch_schedules_enabled.order_delivery
    ORDER_DELIVERY_TRACKING_ENABLED  = var.batch_schedules_enabled.delivery_tracking
    ORDER_CLAIM_REFUND_ENABLED       = var.batch_schedules_enabled.claim_refund
    ORDER_CLAIM_AUTO_APPROVE_ENABLED = var.batch_schedules_enabled.claim_auto_approve
    SETTLEMENT_EXECUTION_ENABLED     = var.batch_schedules_enabled.settlement_execution
  }
  # yml에 enabled 플래그가 둘 더 있지만 스케줄 스위치가 아니라 여기 넣지 않는다.
  #   TOSS_RECONCILIATION_ENABLED          — 대사의 소스 하나를 추가한다(스케줄은 이미 무조건 돈다).
  #                                          켜려면 TOSS_SECRET_KEY 수기 등재가 먼저다.
  #   ORDER_DELIVERY_TRACKING_MOCK_ENABLED — 목 추적 어댑터 조립 여부. delivery_tracking 의 하위 선택지다.
}

# 값이 비밀이 아니라 terraform이 직접 넣는다 — 포트원 시크릿(아래)과 다른 취급이다.
# 끄는 쪽도 파라미터를 지우지 말고 false로 둔다: 지우면 "의도적으로 꺼 둠"과 "등재를 잊음"이
# SSM만 봐서는 구분되지 않는다. 이번 건이 바로 그 구분이 안 돼 묻혀 있던 일이다.
resource "aws_ssm_parameter" "batch_schedule_flags" {
  for_each = local.batch_schedule_flags

  name  = "/${var.project}/${var.env}/batch/${each.key}"
  type  = "String"
  value = tostring(each.value)
}

# --- 포트원 시크릿: 수기 등재 (위 api 경로와 같은 방침) ---
# 대사가 포트원 결제 목록 조회·재조회에 쓴다. 비면 부팅은 되고 대사 회차가 실패로 기록된다.
#   /{project}/{env}/batch/PORTONE_API_SECRET     (SecureString)
#   /{project}/{env}/batch/PORTONE_WEBHOOK_SECRET (SecureString)
# 웹훅 시크릿은 배치가 웹훅을 받지 않아도 필요하다 — 결제 파사드 조립에 검증기가 딸려온다.

# --- JWT RS256 키: 수기 등재 (헤더의 카카오·JWT 키 방침과 동일) ---
# terraform으로 생성하면 개인키가 state에 남아 여기서는 관리하지 않는다.
# 실행 모듈 간 키 공유가 전제라 아래 파라미터를 사람이 직접 등재한다:
#   /{project}/{env}/api/AUTH_JWT_PRIVATE_KEY  (SecureString, PKCS8 PEM) — api만 발급+검증
#   /{project}/{env}/api/AUTH_JWT_PUBLIC_KEY   (String, PEM)
#   /{project}/{env}/seller/AUTH_JWT_PUBLIC_KEY (String, PEM) — api 공개키와 같은 값이어야 api 발급 토큰을 검증한다
#   /{project}/{env}/admin/AUTH_JWT_PUBLIC_KEY  (String, PEM) — 위 seller와 동일 근거 (SCRUM-229)
# 누락 시 결과가 모듈마다 다르다.
#   api: 부팅은 되지만 임시 키 폴백으로 갈라져 모듈 간 검증이 전부 401이 된다
#   seller, admin: dev에서 필수라 공개키 자리에 ${AUTH_JWT_PUBLIC_KEY} 글자가 그대로 들어가 base64 해석 오류로 부팅이 실패한다
# 검증 전용 실행 모듈이 더 늘면 그 모듈 경로에도 공개키를 같은 값으로 등재한다.
