package com.aechak.api.sns

import com.aechak.api.support.IntegrationTestBase
import com.aechak.domain.sns.stats.repository.SnsPostStatsRepository
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired

/**
 * 통합: 게시물 리포스트 수의 원자 갱신. 깨지면 통계 행이 없는 게시물의 수가 빠지거나 수가 음수가 되거나 행이 없는 게시물의 수를 읽지 못한다.
 */
class SnsPostStatsRepositoryIntegrationTest : IntegrationTestBase() {
    @Autowired
    private lateinit var statsRepository: SnsPostStatsRepository

    @Test
    fun `증가는 통계 행이 없으면 행을 만들어 1로 두고 있으면 1씩 올린다`() {
        tx.execute { statsRepository.increaseRepostCount(POST_ID) }
        assertEquals(1L, repostCountOf(POST_ID), "행이 없던 게시물의 리포스트 수가 1이어야 한다")

        tx.execute { statsRepository.increaseRepostCount(POST_ID) }
        assertEquals(2L, repostCountOf(POST_ID), "두 번 올린 리포스트 수는 2여야 한다")
    }

    @Test
    fun `감소는 수가 0이면 갱신하지 않아 0 아래로 내려가지 않는다`() {
        tx.execute { statsRepository.increaseRepostCount(POST_ID) }

        val decreased = tx.execute { statsRepository.decreaseRepostCount(POST_ID) }
        val skipped = tx.execute { statsRepository.decreaseRepostCount(POST_ID) }

        assertEquals(1, decreased, "1인 수는 줄어야 한다")
        assertEquals(0, skipped, "0인 수는 갱신하지 않아야 한다")
        assertEquals(0L, repostCountOf(POST_ID), "리포스트 수가 0 아래로 내려가면 안 된다")
    }

    @Test
    fun `통계 행이 없는 게시물의 리포스트 수는 0으로 읽는다`() {
        assertEquals(0L, repostCountOf(POST_ID), "행이 없으면 0으로 읽어야 한다")
    }

    private fun repostCountOf(postId: Long): Long = tx.execute { statsRepository.findRepostCount(postId) }!!

    companion object {
        private const val POST_ID = 100L
    }
}
