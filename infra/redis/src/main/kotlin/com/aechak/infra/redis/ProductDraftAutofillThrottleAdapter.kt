package com.aechak.infra.redis

import com.aechak.application.product.draft.port.ProductDraftAutofillThrottle
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.stereotype.Component
import java.time.Duration

/**
 * 초안 쿨다운의 Redis 어댑터.
 *
 * setIfAbsent = SET key value NX PX — 검사·설정·만료가 한 명령이라 동시 요청 중 하나만 true 를
 * 받는다. 쪼개면 그 사이로 둘 다 통과해 모델이 두 번 불린다(VerificationCodeStoreAdapter 와
 * 같은 이유, 같은 모양).
 */
@Component
class ProductDraftAutofillThrottleAdapter(
    private val redisTemplate: StringRedisTemplate,
) : ProductDraftAutofillThrottle {
    override fun tryAcquire(
        sellerId: Long,
        ttl: Duration,
    ): Boolean = redisTemplate.opsForValue().setIfAbsent(key(sellerId), "1", ttl) == true

    private fun key(sellerId: Long) = "product:draft:autofill:cooldown:$sellerId"
}
