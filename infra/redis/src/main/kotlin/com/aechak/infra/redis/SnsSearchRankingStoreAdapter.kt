package com.aechak.infra.redis

import com.aechak.application.sns.port.SnsSearchRankingStore
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.stereotype.Component
import java.time.Duration

/** SNS 검색 인기순 순위의 Redis 어댑터. 인기 피드 순위와 같이 키 하나에 게시물 id를 쉼표로 잇고 SET NX EX 한 명령으로 남긴다 */
@Component
class SnsSearchRankingStoreAdapter(
    private val redisTemplate: StringRedisTemplate,
) : SnsSearchRankingStore {
    override fun find(
        scope: String,
        epochMinute: Long,
    ): List<Long>? =
        redisTemplate.opsForValue().get(key(scope, epochMinute))?.let { value ->
            if (value.isEmpty()) emptyList() else value.split(SEPARATOR).map(String::toLong)
        }

    override fun saveIfAbsent(
        scope: String,
        epochMinute: Long,
        postIds: List<Long>,
    ) {
        redisTemplate.opsForValue().setIfAbsent(key(scope, epochMinute), postIds.joinToString(SEPARATOR), TTL)
    }

    private fun key(
        scope: String,
        epochMinute: Long,
    ) = "$PREFIX$scope:$epochMinute"

    companion object {
        private const val PREFIX = "sns-search-popular:"
        private const val SEPARATOR = ","

        // 그 분의 마지막에 첫 페이지를 부른 사람도 30분 동안 이어 내린다
        private val TTL = Duration.ofSeconds(1_860)
    }
}
