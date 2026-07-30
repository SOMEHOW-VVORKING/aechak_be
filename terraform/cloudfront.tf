resource "aws_cloudfront_origin_access_control" "media" {
  name                              = "${local.media_bucket}-oac"
  origin_access_control_origin_type = "s3"
  signing_behavior                  = "always"
  signing_protocol                  = "sigv4"
}

resource "aws_cloudfront_distribution" "media" {
  enabled     = true
  price_class = "PriceClass_200" # 아시아 포함, 전대륙 제외

  # 엣지 서버가 Host 헤더로 배포를 찾는다. 여기 없는 호스트명은 DNS가 맞아도 거부된다
  aliases = [local.media_domain]

  origin {
    domain_name              = aws_s3_bucket.media.bucket_regional_domain_name
    origin_id                = "media-s3"
    origin_access_control_id = aws_cloudfront_origin_access_control.media.id
  }

  default_cache_behavior {
    target_origin_id       = "media-s3"
    viewer_protocol_policy = "redirect-to-https"
    allowed_methods        = ["GET", "HEAD"]
    cached_methods         = ["GET", "HEAD"]
    cache_policy_id        = "658327ea-f89d-4fab-a63d-7e88639e58f6" # Managed-CachingOptimized
  }

  restrictions {
    geo_restriction { restriction_type = "none" }
  }

  # acm_certificate_arn을 쓰면 나머지 둘도 필수다(빠지면 apply 실패).
  # sni-only 대신 vip를 쓰면 전용 IP 방식이라 인증서당 월 $600이 붙는다.
  viewer_certificate {
    acm_certificate_arn      = local.cf_cert_arn
    ssl_support_method       = "sni-only"
    minimum_protocol_version = "TLSv1.2_2021"
  }
}

# 이 배포만 media 버킷 읽기 허용
resource "aws_s3_bucket_policy" "media" {
  bucket = aws_s3_bucket.media.id

  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [{
      Effect    = "Allow"
      Principal = { Service = "cloudfront.amazonaws.com" }
      Action    = "s3:GetObject"
      Resource  = "${aws_s3_bucket.media.arn}/*"
      Condition = {
        StringEquals = { "AWS:SourceArn" = aws_cloudfront_distribution.media.arn }
      }
    }]
  })

  depends_on = [aws_s3_bucket_public_access_block.media]
}
