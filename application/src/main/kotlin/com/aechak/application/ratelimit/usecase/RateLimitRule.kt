package com.aechak.application.ratelimit.usecase

import java.time.Duration

/** 창 하나의 한도. 창 안에서 limit번까지 받는다 */
data class RateLimit(
    val limit: Int,
    val window: Duration,
)

/** 사용자별로 세는 행동 하나. BC마다 enum으로 구현한다 */
interface RateLimitRule {
    /** 카운터 키에 들어가는 이름. 다른 규칙과 겹치면 함께 센다 */
    val key: String

    /** 창이 여럿이면 모두 한도 아래일 때만 받는다 */
    val limits: List<RateLimit>
}
