package com.aechak.websecurity.config

import org.springframework.boot.context.properties.ConfigurationProperties

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
