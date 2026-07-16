package com.aechak.infra.redis

import com.aechak.application.auth.port.RefreshTokenEntry
import com.aechak.application.auth.port.RefreshTokenRef
import com.aechak.application.auth.port.RefreshTokenStore
import org.springframework.data.redis.core.ScanOptions
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.stereotype.Component
import java.time.Duration
import java.time.Instant

/**
 * RefreshTokenStore 포트의 Redis 어댑터.
 *
 * - 키: refresh:{userId}:{tokenId} · TTL은 Redis에 위임 — 만료 정리 배치가 불필요해진다.
 * - 값: 현행 "A|{sha256hex}" / 회전 강등 "R|{successorJti}|{iatEpochSec}|{expEpochSec}".
 * - deleteAll은 SCAN 기반 — 유저당 키 소수·호출 드묾이라 수용. 트래픽 증가 시 SET 인덱스 전환.
 */
@Component
class RedisRefreshTokenStoreAdapter(
    private val redisTemplate: StringRedisTemplate,
) : RefreshTokenStore {
    override fun save(
        userId: Long,
        tokenId: String,
        tokenHash: String,
        ttl: Duration,
    ) {
        redisTemplate.opsForValue().set(key(userId, tokenId), "$ACTIVE$SEP$tokenHash", ttl)
    }

    override fun find(
        userId: Long,
        tokenId: String,
    ): RefreshTokenEntry? {
        val raw = redisTemplate.opsForValue().get(key(userId, tokenId)) ?: return null
        val parts = raw.split(SEP)
        return when (parts.firstOrNull()) {
            ACTIVE -> {
                RefreshTokenEntry.Active(parts[1])
            }

            ROTATED -> {
                RefreshTokenEntry.Rotated(
                    RefreshTokenRef(
                        tokenId = parts[1],
                        issuedAt = Instant.ofEpochSecond(parts[2].toLong()),
                        expiresAt = Instant.ofEpochSecond(parts[3].toLong()),
                    ),
                )
            }

            else -> {
                null
            }
        }
    }

    override fun markRotated(
        userId: Long,
        tokenId: String,
        successor: RefreshTokenRef,
        grace: Duration,
    ) {
        redisTemplate.opsForValue().set(
            key(userId, tokenId),
            "$ROTATED$SEP${successor.tokenId}$SEP${successor.issuedAt.epochSecond}$SEP${successor.expiresAt.epochSecond}",
            grace,
        )
    }

    override fun delete(
        userId: Long,
        tokenId: String,
    ) {
        redisTemplate.delete(key(userId, tokenId))
    }

    override fun deleteAll(userId: Long) {
        val options =
            ScanOptions
                .scanOptions()
                .match("$PREFIX$userId:*")
                .count(SCAN_COUNT)
                .build()
        redisTemplate.scan(options).use { cursor ->
            val keys = cursor.asSequence().toList()
            if (keys.isNotEmpty()) redisTemplate.delete(keys)
        }
    }

    private fun key(
        userId: Long,
        tokenId: String,
    ) = "$PREFIX$userId:$tokenId"

    companion object {
        private const val PREFIX = "refresh:"
        private const val SEP = "|"
        private const val ACTIVE = "A"
        private const val ROTATED = "R"
        private const val SCAN_COUNT = 100L
    }
}
