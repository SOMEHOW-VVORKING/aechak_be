package com.aechak.api.sns

import com.aechak.api.support.IntegrationTestBase
import com.aechak.domain.sns.repost.repository.RepostRepository
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.jdbc.core.JdbcTemplate
import java.time.LocalDateTime

/**
 * 통합: 리포스트 행의 원자 삽입과 삭제. 깨지면 반복 리포스트가 수를 두 번 올리거나 끄기가 수를 잘못 내리거나
 * 리포스트 시각이 게시 시각과 다른 시계로 남아 팔로잉 피드에서 게시와 리포스트의 순서가 뒤집힌다.
 */
class RepostRepositoryIntegrationTest : IntegrationTestBase() {
    @Autowired
    private lateinit var repostRepository: RepostRepository

    @Autowired
    private lateinit var jdbcTemplate: JdbcTemplate

    @Test
    fun `처음 삽입은 true이고 같은 쌍을 다시 넣으면 false이며 행은 하나다`() {
        val first = tx.execute { repostRepository.insertIfAbsent(REPOSTER_ID, POST_ID, AT) }
        val again = tx.execute { repostRepository.insertIfAbsent(REPOSTER_ID, POST_ID, AT.plusMinutes(1)) }

        assertEquals(true, first, "처음 리포스트는 새로 들어가야 한다")
        assertEquals(false, again, "같은 쌍은 다시 들어가면 안 된다")
        assertEquals(1, rowCount(), "같은 쌍의 행은 하나여야 한다")
    }

    @Test
    fun `두 시각 열은 넘긴 앱 시각 그대로다`() {
        tx.execute { repostRepository.insertIfAbsent(REPOSTER_ID, POST_ID, AT) }

        assertEquals(AT, timeOf("created_at"), "created_at은 넘긴 앱 시각이어야 한다")
        assertEquals(AT, timeOf("updated_at"), "updated_at은 넘긴 앱 시각이어야 한다")
    }

    @Test
    fun `삭제는 지운 행 수를 돌려주고 다른 사람의 리포스트는 남긴다`() {
        tx.execute { repostRepository.insertIfAbsent(REPOSTER_ID, POST_ID, AT) }
        tx.execute { repostRepository.insertIfAbsent(OTHER_REPOSTER_ID, POST_ID, AT) }

        val deleted = tx.execute { repostRepository.delete(REPOSTER_ID, POST_ID) }
        val missing = tx.execute { repostRepository.delete(REPOSTER_ID, POST_ID) }

        assertEquals(1, deleted, "있는 리포스트를 지우면 1이어야 한다")
        assertEquals(0, missing, "없는 리포스트를 지우면 0이어야 한다")
        assertEquals(1, rowCount(), "다른 사람의 리포스트는 남아야 한다")
    }

    private fun timeOf(column: String): LocalDateTime? =
        jdbcTemplate.queryForObject(
            "select $column from sns_reposts where reposter_id = ? and post_id = ?",
            LocalDateTime::class.java,
            REPOSTER_ID,
            POST_ID,
        )

    private fun rowCount(): Int = jdbcTemplate.queryForObject("select count(*) from sns_reposts", Int::class.java)!!

    companion object {
        private const val REPOSTER_ID = 10L
        private const val OTHER_REPOSTER_ID = 20L
        private const val POST_ID = 100L

        // DATETIME(6)이 담는 마이크로초까지만 둔다
        private val AT = LocalDateTime.of(2026, 11, 5, 9, 30, 15, 123_456_000)
    }
}
