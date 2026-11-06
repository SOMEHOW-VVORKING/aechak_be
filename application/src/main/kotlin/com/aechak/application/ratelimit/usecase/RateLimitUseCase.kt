package com.aechak.application.ratelimit.usecase

/** 사용자별 요청 빈도 제한. Redis를 부르므로 Facade가 트랜잭션을 열기 전에 부른다 */
interface RateLimitUseCase {
    /** 규칙의 창 하나라도 찼으면 429 90012를 던지고 아무 창도 세지 않는다. Redis가 실패하면 통과시킨다 */
    fun check(
        userId: Long,
        rule: RateLimitRule,
    )
}
