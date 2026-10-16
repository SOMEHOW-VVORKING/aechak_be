package com.aechak.api.auth.config

import org.springframework.boot.context.properties.ConfigurationProperties
import java.time.Duration

@ConfigurationProperties("auth.rejoin")
data class RejoinProperties(
    val blockedPeriod: Duration = Duration.ofDays(30),
)
