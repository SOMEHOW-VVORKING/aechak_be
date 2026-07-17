package com.aechak.application.auth.service

import java.time.Duration

/**
 * 토큰 수명 정책 — 값은 실행 모듈 설정(auth.token.*)이 빈으로 공급한다.
 * accessTtl 수치는 팀 미확정 — 기본 제안값 30분.
 */
data class TokenPolicy(
    val accessTtl: Duration,
    val refreshTtl: Duration,
    val rotationGrace: Duration,
)
