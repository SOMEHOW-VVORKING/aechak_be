package com.aechak.webcommon.http

/** 표준(RFC) 밖 커스텀 HTTP 헤더 이름의 정본 — 스프링 HttpHeaders에 없는 것만 둔다 */
object CustomHttpHeaders {
    /** 생성형 EP의 클라이언트 멱등키. IETF 초안·Stripe 관례를 따른 이름 */
    const val IDEMPOTENCY_KEY = "Idempotency-Key"
}
