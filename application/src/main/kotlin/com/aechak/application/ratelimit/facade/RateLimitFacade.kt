package com.aechak.application.ratelimit.facade

import com.aechak.application.ratelimit.port.RateLimitCounter
import com.aechak.application.ratelimit.usecase.RateLimitRule
import com.aechak.application.ratelimit.usecase.RateLimitUseCase
import com.aechak.common.error.BusinessException
import com.aechak.common.error.CommonErrorCode
import org.slf4j.LoggerFactory
import org.springframework.dao.DataAccessException
import org.springframework.stereotype.Service
import java.time.Instant

@Service
class RateLimitFacade(
    private val counter: RateLimitCounter,
) : RateLimitUseCase {
    private val log = LoggerFactory.getLogger(javaClass)

    override fun check(
        userId: Long,
        rule: RateLimitRule,
    ) {
        val now = Instant.now().epochSecond
        val counters =
            rule.limits.associateBy { limit ->
                val windowSeconds = limit.window.seconds
                // 창 번호가 키에 있어 만료를 걸지 못한 키가 남아도 다음 창에서는 새 키를 센다
                "$PREFIX${rule.key}:$userId:$windowSeconds:${now / windowSeconds}"
            }
        // 같은 길이의 창이 둘이면 키가 겹쳐 한쪽 한도가 사라진다
        require(counters.size == rule.limits.size) { "같은 길이의 창이 둘인 빈도 제한 규칙: ${rule.key}" }
        val acquired =
            try {
                counter.tryAcquire(counters)
            } catch (e: DataAccessException) {
                // Redis 장애로 쓰기를 막지 않는다. 대기는 spring.data.redis.timeout(1초)까지다
                log.warn("빈도 제한 확인 실패로 통과시킴 (rule={}, userId={})", rule.key, userId, e)
                true
            }
        if (!acquired) throw BusinessException(CommonErrorCode.TOO_MANY_REQUESTS)
    }

    companion object {
        const val PREFIX = "rate-limit:"
    }
}
