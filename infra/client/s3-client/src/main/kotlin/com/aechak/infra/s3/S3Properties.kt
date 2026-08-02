package com.aechak.infra.s3

import org.springframework.boot.context.properties.ConfigurationProperties
import java.time.Duration

@ConfigurationProperties("aws.s3")
data class S3Properties(
    val mediaBucket: String,
    val docsBucket: String,
    val presignTtl: Duration = Duration.ofMinutes(10),
    /** media 버킷 앞단(CDN) 공개 도메인 — 표시용 URL 조립(key→URL)에 사용. 예: https://cdn.aechak.com */
    val mediaPublicBaseUrl: String = "",
)
