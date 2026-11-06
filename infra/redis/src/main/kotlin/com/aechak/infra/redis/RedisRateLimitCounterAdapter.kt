package com.aechak.infra.redis

import com.aechak.application.ratelimit.port.RateLimitCounter
import com.aechak.application.ratelimit.usecase.RateLimit
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.data.redis.core.script.RedisScript
import org.springframework.stereotype.Component

/**
 * 빈도 제한 카운터의 Redis 어댑터. 확인, 증가, 만료를 스크립트 하나로 실행해
 * 동시 요청이 한도를 넘기지 않고 만료 없는 키도 남지 않는다.
 */
@Component
class RedisRateLimitCounterAdapter(
    private val redisTemplate: StringRedisTemplate,
) : RateLimitCounter {
    override fun tryAcquire(counters: Map<String, RateLimit>): Boolean {
        val args = counters.values.flatMap { listOf(it.limit.toString(), it.window.seconds.toString()) }
        return redisTemplate.execute(SCRIPT, counters.keys.toList(), *args.toTypedArray()) == 1L
    }

    companion object {
        // ARGV는 키마다 한도와 창 초를 차례로 둔다
        private val SCRIPT =
            RedisScript.of<Long>(
                """
                for i = 1, #KEYS do
                  if tonumber(redis.call('GET', KEYS[i]) or '0') >= tonumber(ARGV[i * 2 - 1]) then return 0 end
                end
                for i = 1, #KEYS do
                  if redis.call('INCR', KEYS[i]) == 1 then redis.call('EXPIRE', KEYS[i], ARGV[i * 2]) end
                end
                return 1
                """.trimIndent(),
                Long::class.java,
            )
    }
}
