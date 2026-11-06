package com.aechak.infra.redis

import com.aechak.application.sns.port.SnsPopularSnapshotStore
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.stereotype.Component
import java.time.Duration

/**
 * 인기 피드 순위의 Redis 어댑터. 키 하나에 게시물 id를 쉼표로 이은 문자열을 둔다.
 * 저장은 SET NX EX 한 명령이라 같은 분에 먼저 저장한 순위가 남고 만료 없는 키가 생기지 않는다.
 */
@Component
class SnsPopularSnapshotStoreAdapter(
    private val redisTemplate: StringRedisTemplate,
) : SnsPopularSnapshotStore {
    override fun find(epochMinute: Long): List<Long>? =
        redisTemplate.opsForValue().get(key(epochMinute))?.let { value ->
            if (value.isEmpty()) emptyList() else value.split(SEPARATOR).map(String::toLong)
        }

    override fun saveIfAbsent(
        epochMinute: Long,
        postIds: List<Long>,
    ) {
        redisTemplate.opsForValue().setIfAbsent(key(epochMinute), postIds.joinToString(SEPARATOR), TTL)
    }

    private fun key(epochMinute: Long) = "$PREFIX$epochMinute"

    companion object {
        private const val PREFIX = "sns-popular:"
        private const val SEPARATOR = ","

        // 그 분의 마지막에 첫 페이지를 부른 사람도 30분 동안 이어 내린다
        private val TTL = Duration.ofSeconds(1_860)
    }
}
