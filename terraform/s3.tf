# 버킷 분리 기준 = 도메인이 아니라 성격 (공개 media / 민감 docs)
# 도메인 구분은 prefix: products/ reviews/ users/ ...

# 계정 ID suffix — S3 전역 네임스페이스 충돌(BucketAlreadyExists) 방지
locals {
  media_bucket = "${var.project}-media-${var.env}-${data.aws_caller_identity.me.account_id}"
  docs_bucket  = "${var.project}-docs-${var.env}-${data.aws_caller_identity.me.account_id}"
}

# ── media: 공개 이미지 (CloudFront OAC 경유로만) ─────────
resource "aws_s3_bucket" "media" {
  bucket = local.media_bucket
}

resource "aws_s3_bucket_public_access_block" "media" {
  bucket                  = aws_s3_bucket.media.id
  block_public_acls       = true
  block_public_policy     = true
  ignore_public_acls      = true
  restrict_public_buckets = true
}

resource "aws_s3_bucket_server_side_encryption_configuration" "media" {
  bucket = aws_s3_bucket.media.id
  rule {
    apply_server_side_encryption_by_default {
      sse_algorithm = "AES256"
    }
  }
}

# presigned PUT 직접 업로드용. media에 올리는 쪽은 구매자 웹(리뷰 이미지)과 셀러 웹(상품 이미지)뿐이다.
resource "aws_s3_bucket_cors_configuration" "media" {
  bucket = aws_s3_bucket.media.id
  cors_rule {
    allowed_methods = ["PUT", "GET"]
    allowed_origins = concat(local.buyer_origins, local.seller_origins)
    allowed_headers = ["*"]
    max_age_seconds = 3000
  }
}

# tmp/ = 검증 전 업로드 staging, 1일 후 자동 삭제
resource "aws_s3_bucket_lifecycle_configuration" "media" {
  bucket = aws_s3_bucket.media.id
  rule {
    id     = "expire-tmp"
    status = "Enabled"
    filter { prefix = "tmp/" }
    expiration { days = 1 }
  }
}

# ── docs: 셀러 서류 등 민감 파일 (private + KMS) ─────────
resource "aws_s3_bucket" "docs" {
  bucket = local.docs_bucket
}

resource "aws_s3_bucket_public_access_block" "docs" {
  bucket                  = aws_s3_bucket.docs.id
  block_public_acls       = true
  block_public_policy     = true
  ignore_public_acls      = true
  restrict_public_buckets = true
}

resource "aws_s3_bucket_versioning" "docs" {
  bucket = aws_s3_bucket.docs.id
  versioning_configuration {
    status = "Enabled"
  }
}

resource "aws_kms_key" "docs" {
  description             = "${var.project} seller docs (${var.env})"
  deletion_window_in_days = 7
}

resource "aws_s3_bucket_server_side_encryption_configuration" "docs" {
  bucket = aws_s3_bucket.docs.id
  rule {
    apply_server_side_encryption_by_default {
      sse_algorithm     = "aws:kms"
      kms_master_key_id = aws_kms_key.docs.arn
    }
  }
}

# 입점 서류 presigned PUT 직접 업로드용 — 올리는 쪽은 셀러 웹뿐이다.
resource "aws_s3_bucket_cors_configuration" "docs" {
  bucket = aws_s3_bucket.docs.id
  cors_rule {
    allowed_methods = ["PUT"]
    allowed_origins = local.seller_origins
    allowed_headers = ["*"]
    max_age_seconds = 3000
  }
}

# docs는 CloudFront 없음 — 셀러가 presigned PUT으로 올리고, 열람은 짧은 TTL presigned GET으로만 한다
