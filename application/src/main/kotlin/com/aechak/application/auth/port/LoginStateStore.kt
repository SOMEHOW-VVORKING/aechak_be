package com.aechak.application.auth.port

import java.time.Duration

/**
 * 웹 로그인 state 저장소 아웃바운드 포트 — 구현은 infra:redis.
 * state = 서버 콜백 플로우의 로그인 CSRF 방어 토큰이자 return URL의 운반체.
 * 반드시 1회용이어야 한다 — 소비(consume)는 원자적 "읽고 즉시 삭제"로 구현한다(재사용 = 공격 신호).
 */
interface LoginStateStore {
    fun save(state: String, returnUrl: String, ttl: Duration)

    /** 1회 소비 — 저장된 returnUrl을 돌려주고 즉시 삭제. 없으면(만료·재사용·위조) null. */
    fun consume(state: String): String?
}
