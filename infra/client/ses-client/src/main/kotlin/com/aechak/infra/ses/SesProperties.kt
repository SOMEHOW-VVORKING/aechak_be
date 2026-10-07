package com.aechak.infra.ses

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties("aws.ses")
data class SesProperties(
    val from: String = "",
)
