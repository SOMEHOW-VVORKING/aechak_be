package com.aechak.api.support

import com.aechak.application.ratelimit.port.RateLimitCounter
import com.aechak.application.ratelimit.usecase.RateLimit

/**
 * 빈도 제한 카운터. 꺼져 있으면 항상 통과시켜 한 사용자가 한도보다 많이 요청하는 테스트가 429를 받지 않는다.
 * 켜면 실제 Redis 어댑터로 센다. 켠 테스트는 끝날 때 끈다.
 * 켜면 창 번호를 0으로 고정해 테스트가 분 경계를 넘어도 같은 창에서 센다.
 */
class SwitchableRateLimitCounter(
    private val redis: RateLimitCounter,
) : RateLimitCounter {
    @Volatile
    var enabled = false

    override fun tryAcquire(counters: Map<String, RateLimit>): Boolean =
        !enabled || redis.tryAcquire(counters.mapKeys { (key, _) -> "${key.substringBeforeLast(':')}:0" })
}
