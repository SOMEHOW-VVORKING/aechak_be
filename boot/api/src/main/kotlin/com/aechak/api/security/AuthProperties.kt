package com.aechak.api.security

import org.springframework.boot.context.properties.ConfigurationProperties
import java.time.Duration

/** 토큰 수명 설정(auth.token.*) — access TTL 수치는 팀 미확정(기본 제안 30분). */
@ConfigurationProperties("auth.token")
data class AuthTokenProperties(
    val accessTtl: Duration = Duration.ofMinutes(30),
    val refreshTtl: Duration = Duration.ofDays(7),
    val rotationGrace: Duration = Duration.ofSeconds(60),
)

/** 웹(서버 콜백) 로그인 설정(auth.web.*) — return 화이트리스트는 정확 일치로만 검증한다. */
@ConfigurationProperties("auth.web")
data class WebLoginProperties(
    val returnUrls: List<String> = emptyList(),
    /** provider에 등록하는 서버 콜백 주소의 밑동(…/auth/callback까지). */
    val callbackBaseUrl: String = "",
    val stateTtl: Duration = Duration.ofMinutes(5),
)

/**
 * 자체 RS256 키(auth.jwt.*) — PEM 문자열.
 * prod는 SSM Parameter Store(/aechak/{env}/auth/jwt-private-key) 주입, local은 .env.
 * 비워두면 개발용 임시 키를 생성한다(재시작 시 기존 토큰 전부 무효 — local 한정).
 */
@ConfigurationProperties("auth.jwt")
data class JwtKeyProperties(
    val privateKey: String = "",
    val publicKey: String = "",
)
