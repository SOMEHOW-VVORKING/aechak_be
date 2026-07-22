package com.aechak.infra.s3

import org.springframework.boot.context.properties.ConfigurationProperties
import java.time.Duration

@ConfigurationProperties("aws.s3")
data class S3Properties(
    val mediaBucket: String,
    val docsBucket: String,
    val presignTtl: Duration = Duration.ofMinutes(10),
)
