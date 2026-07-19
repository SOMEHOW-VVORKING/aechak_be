package com.aechak.websecurity.config

import org.springframework.boot.context.properties.ConfigurationProperties
import java.time.Duration

/** 토큰 수명 설정(auth.token.*) — access TTL 수치는 팀 미확정(기본 제안 30분). */
@ConfigurationProperties("auth.token")
data class AuthTokenProperties(
    val accessTtl: Duration = Duration.ofMinutes(30),
    val refreshTtl: Duration = Duration.ofDays(7),
    val rotationGrace: Duration = Duration.ofSeconds(60),
)
