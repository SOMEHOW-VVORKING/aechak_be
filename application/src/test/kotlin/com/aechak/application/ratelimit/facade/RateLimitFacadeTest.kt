package com.aechak.application.ratelimit.facade

import com.aechak.application.ratelimit.port.RateLimitCounter
import com.aechak.application.ratelimit.usecase.RateLimit
import com.aechak.application.ratelimit.usecase.RateLimitRule
import com.aechak.common.error.BusinessException
import com.aechak.common.error.CommonErrorCode
import java.time.Duration
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * 계약: 창마다 창 번호(epochSeconds / windowSeconds)를 담은 키로 세고 찬 창이 있으면 90012, 저장소가 실패하면 통과시키는지.
 * 깨지면 만료를 걸지 못한 키가 사용자를 계속 막거나 Redis 장애가 SNS 쓰기를 모두 막는다.
 */
class RateLimitFacadeTest {
    private object Rule : RateLimitRule {
        override val key = "test:RULE"
        override val limits = listOf(RateLimit(5, Duration.ofMinutes(1)), RateLimit(30, Duration.ofHours(1)))
    }

    @Test
    fun `창마다 창 번호를 담은 키와 한도를 넘긴다`() {
        var received: Map<String, RateLimit> = emptyMap()
        val before = Instant.now().epochSecond
        RateLimitFacade(
            object : RateLimitCounter {
                override fun tryAcquire(counters: Map<String, RateLimit>) = true.also { received = counters }
            },
        ).check(7, Rule)
        val after = Instant.now().epochSecond

        val minute = received.keys.single { it.startsWith("rate-limit:test:RULE:7:60:") }
        val hour = received.keys.single { it.startsWith("rate-limit:test:RULE:7:3600:") }
        assertTrue(minute.substringAfterLast(':').toLong() in before / 60..after / 60, "1분 창 번호가 키 끝에 있어야 한다: $minute")
        assertTrue(hour.substringAfterLast(':').toLong() in before / 3_600..after / 3_600, "1시간 창 번호가 키 끝에 있어야 한다: $hour")
        assertEquals(5, received.getValue(minute).limit)
        assertEquals(30, received.getValue(hour).limit)
    }

    @Test
    fun `찬 창이 있으면 429 90012다`() {
        val error = assertFailsWith<BusinessException> { RateLimitFacade(counter { false }).check(7, Rule) }

        assertEquals(CommonErrorCode.TOO_MANY_REQUESTS, error.errorCode)
    }

    @Test
    fun `저장소가 실패하면 통과시킨다`() {
        RateLimitFacade(counter { throw IllegalStateException("redis down") }).check(7, Rule)
    }

    private fun counter(result: () -> Boolean) =
        object : RateLimitCounter {
            override fun tryAcquire(counters: Map<String, RateLimit>) = result()
        }
}
