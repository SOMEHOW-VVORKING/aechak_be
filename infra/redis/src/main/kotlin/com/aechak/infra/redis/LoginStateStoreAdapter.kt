package com.aechak.infra.redis

import com.aechak.application.auth.port.LoginStateStore
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.stereotype.Component
import java.time.Duration

/**
 * LoginStateStore 포트의 Redis 어댑터.
 * 소비는 GETDEL(읽기+삭제 원자 연산) — "확인 후 삭제" 두 단계로 쪼개면 그 틈에 재사용이 성립한다.
 */
@Component
class LoginStateStoreAdapter(
    private val redisTemplate: StringRedisTemplate,
) : LoginStateStore {
    override fun save(
        state: String,
        returnUrl: String,
        ttl: Duration,
    ) {
        redisTemplate.opsForValue().set(key(state), returnUrl, ttl)
    }

    override fun consume(state: String): String? = redisTemplate.opsForValue().getAndDelete(key(state))

    private fun key(state: String) = "$PREFIX$state"

    companion object {
        private const val PREFIX = "login-state:"
    }
}
