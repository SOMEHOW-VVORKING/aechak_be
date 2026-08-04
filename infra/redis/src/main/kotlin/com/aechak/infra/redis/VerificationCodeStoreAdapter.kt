package com.aechak.infra.redis

import com.aechak.application.user.verification.port.DailySendCounts
import com.aechak.application.user.verification.port.IssuedVerificationCode
import com.aechak.application.user.verification.port.VerificationCodeStore
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.stereotype.Component
import java.time.Duration
import java.time.Instant
import java.util.Date

/**
 * VerificationCodeStore 포트의 Redis 어댑터.
 * 일 카운터는 INCR(원자) + 버킷 만료 재설정 — 증가와 판정을 쪼개지 않아 동시 발송에도 상한이 뚫리지 않는다.
 */
@Component
class VerificationCodeStoreAdapter(
    private val redisTemplate: StringRedisTemplate,
) : VerificationCodeStore {
    override fun saveCode(
        userId: Long,
        phoneNumber: String,
        code: String,
        ttl: Duration,
    ) {
        redisTemplate.opsForValue().set(codeKey(userId), "$phoneNumber:$code", ttl)
        redisTemplate.delete(attemptKey(userId))
    }

    override fun findCode(userId: Long): IssuedVerificationCode? {
        val stored = redisTemplate.opsForValue().get(codeKey(userId)) ?: return null
        val phoneNumber = stored.substringBeforeLast(':')
        val code = stored.substringAfterLast(':')
        return IssuedVerificationCode(phoneNumber, code)
    }

    override fun removeCode(userId: Long) {
        redisTemplate.delete(listOf(codeKey(userId), attemptKey(userId)))
    }

    override fun isInCooldown(userId: Long): Boolean = redisTemplate.hasKey(cooldownKey(userId))

    override fun startCooldown(
        userId: Long,
        ttl: Duration,
    ) {
        redisTemplate.opsForValue().set(cooldownKey(userId), "1", ttl)
    }

    override fun incrementDailyCounts(
        userId: Long,
        phoneNumber: String,
        dateKey: String,
        expireAt: Instant,
    ): DailySendCounts {
        val userKey = dailyUserKey(userId, dateKey)
        val phoneKey = dailyPhoneKey(phoneNumber, dateKey)
        val userCount = redisTemplate.opsForValue().increment(userKey)!!
        redisTemplate.expireAt(userKey, Date.from(expireAt))
        val phoneCount = redisTemplate.opsForValue().increment(phoneKey)!!
        redisTemplate.expireAt(phoneKey, Date.from(expireAt))
        return DailySendCounts(userCount, phoneCount)
    }

    override fun rollbackDailyCounts(
        userId: Long,
        phoneNumber: String,
        dateKey: String,
    ) {
        redisTemplate.opsForValue().decrement(dailyUserKey(userId, dateKey))
        redisTemplate.opsForValue().decrement(dailyPhoneKey(phoneNumber, dateKey))
    }

    override fun incrementAttempts(
        userId: Long,
        ttl: Duration,
    ): Long {
        val key = attemptKey(userId)
        val attempts = redisTemplate.opsForValue().increment(key)!!
        if (attempts == 1L) {
            redisTemplate.expire(key, ttl)
        }
        return attempts
    }

    private fun codeKey(userId: Long) = "sms:code:$userId"

    private fun attemptKey(userId: Long) = "sms:attempt:$userId"

    private fun cooldownKey(userId: Long) = "sms:cooldown:$userId"

    private fun dailyUserKey(
        userId: Long,
        dateKey: String,
    ) = "sms:daily:user:$userId:$dateKey"

    private fun dailyPhoneKey(
        phoneNumber: String,
        dateKey: String,
    ) = "sms:daily:phone:$phoneNumber:$dateKey"
}
