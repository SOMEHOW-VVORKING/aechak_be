package com.aechak.api.sns

import com.aechak.api.support.IntegrationTestBase
import com.aechak.domain.sns.stats.SnsUserStats
import com.aechak.domain.sns.stats.repository.SnsUserStatsRepository
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired

/**
 * 통합: SNS 사용자 통계 카운터의 원자 갱신. 깨지면 통계 행이 없는 사용자의 수가 빠지거나 수가 음수가 된다.
 */
class SnsUserStatsRepositoryIntegrationTest : IntegrationTestBase() {
    @Autowired
    private lateinit var statsRepository: SnsUserStatsRepository

    @Test
    fun `증가는 통계 행이 없으면 행을 만들고 해당 수만 1로 둔다`() {
        tx.execute { statsRepository.increaseFollowerCount(USER_ID) }
        tx.execute { statsRepository.increaseFollowingCount(OTHER_USER_ID) }

        val stats = statsOf(USER_ID)
        assertEquals(1, stats.followerCount, "행이 없던 사용자의 팔로워 수가 1이어야 한다")
        assertEquals(0, stats.followingCount, "다른 수는 0이어야 한다")
        assertEquals(0, stats.postCount, "다른 수는 0이어야 한다")
        assertEquals(1, statsOf(OTHER_USER_ID).followingCount, "행이 없던 사용자의 팔로잉 수가 1이어야 한다")
    }

    @Test
    fun `증가는 이미 있는 행의 수를 1씩 올린다`() {
        repeat(2) { tx.execute { statsRepository.increaseFollowerCount(USER_ID) } }

        assertEquals(2, statsOf(USER_ID).followerCount, "두 번 올린 팔로워 수는 2여야 한다")
    }

    @Test
    fun `감소는 수가 0이면 갱신하지 않아 0 아래로 내려가지 않는다`() {
        tx.execute { statsRepository.increaseFollowingCount(USER_ID) }

        val decreased = tx.execute { statsRepository.decreaseFollowingCount(USER_ID) }
        val skipped = tx.execute { statsRepository.decreaseFollowingCount(USER_ID) }
        val skippedFollower = tx.execute { statsRepository.decreaseFollowerCount(USER_ID) }

        assertEquals(1, decreased, "1인 수는 줄어야 한다")
        assertEquals(0, skipped, "0인 수는 갱신하지 않아야 한다")
        assertEquals(0, skippedFollower, "0인 수는 갱신하지 않아야 한다")
        val stats = statsOf(USER_ID)
        assertEquals(0, stats.followingCount, "팔로잉 수가 0 아래로 내려가면 안 된다")
        assertEquals(0, stats.followerCount, "팔로워 수가 0 아래로 내려가면 안 된다")
    }

    @Test
    fun `감소는 통계 행이 없으면 아무 행도 만들지 않는다`() {
        val updated = tx.execute { statsRepository.decreaseFollowerCount(USER_ID) }

        assertEquals(0, updated, "행이 없으면 갱신 행 수가 0이어야 한다")
        assertEquals(null, tx.execute { statsRepository.findByUserId(USER_ID) }, "감소가 행을 만들면 안 된다")
    }

    @Test
    fun `두 사용자 잠금은 없는 행을 0으로 만들고 있는 행의 수는 그대로 둔다`() {
        tx.execute { statsRepository.increaseFollowerCount(OTHER_USER_ID) }

        tx.execute { statsRepository.lockInUserIdOrder(OTHER_USER_ID, USER_ID) }

        val created = statsOf(USER_ID)
        assertEquals(listOf(0, 0, 0), listOf(created.followerCount, created.followingCount, created.postCount), "새 행은 0이어야 한다")
        assertEquals(1, statsOf(OTHER_USER_ID).followerCount, "있던 행의 수는 바뀌면 안 된다")
    }

    private fun statsOf(userId: Long): SnsUserStats =
        requireNotNull(tx.execute { statsRepository.findByUserId(userId) }) { "$userId 의 통계 행이 있어야 한다" }

    companion object {
        private const val USER_ID = 10L
        private const val OTHER_USER_ID = 20L
    }
}
