package com.aechak.api.sns

import com.aechak.application.sns.withdrawal.usecase.SnsWithdrawalUseCase
import com.aechak.application.user.withdrawal.usecase.WithdrawalUseCase
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** 탈퇴하면 그 사용자의 SNS 게시물, 관계, 반응, 댓글이 정리되고 상대의 수가 준다 */
class SnsWithdrawalCleanupIntegrationTest : SnsPostIntegrationTestBase() {
    @Autowired
    private lateinit var withdrawalUseCase: WithdrawalUseCase

    @Autowired
    private lateinit var snsWithdrawalUseCase: SnsWithdrawalUseCase

    @Test
    fun `탈퇴한 사용자의 팔로우가 두 방향 모두 지워지고 상대의 수가 주며 탈퇴한 사용자의 통계 행은 사라진다`() {
        val withdrawn = onboardedUser()
        val followee = onboardedUser()
        val follower = onboardedUser()
        val bystander = onboardedUser()
        relate(withdrawn, followee, "follow")
        relate(follower, withdrawn, "follow")
        relate(follower, followee, "follow")
        relate(bystander, followee, "follow")

        withdrawalUseCase.withdraw(withdrawn)

        assertEquals(0, count("select count(*) from sns_follows where ? in (follower_id, followee_id)", withdrawn))
        assertEquals(2 to 0, followCounts(followee))
        assertEquals(0 to 1, followCounts(follower))
        assertEquals(0 to 1, followCounts(bystander))
        assertEquals(0, count("select count(*) from sns_user_stats where user_id = ?", withdrawn))
    }

    @Test
    fun `탈퇴 트랜잭션이 첫 읽기를 한 뒤에 커밋된 팔로우와 리포스트도 지우고 상대의 수를 내린다`() {
        val withdrawn = onboardedUser()
        val lateFollower = onboardedUser()
        val postId = createdPublicId(onboardedUser())

        tx.execute {
            // 일반 SELECT 한 번으로 이 트랜잭션의 스냅샷을 만든다
            jdbcTemplate.queryForObject("select count(*) from sns_follows", Int::class.java)
            Thread {
                relate(lateFollower, withdrawn, "follow")
                repost(postId, withdrawn).andExpect(status().isCreated)
            }.apply { start() }.join()
            snsWithdrawalUseCase.cleanUp(withdrawn)
        }

        assertEquals(0 to 0, followCounts(lateFollower))
        assertEquals(0, repostCountOf(postId))
        assertEquals(0, count("select count(*) from sns_follows where follower_id = ?", lateFollower))
    }

    @Test
    fun `탈퇴한 사용자의 리포스트와 저장과 좋아요가 지워지고 원글 리포스트 수와 좋아요 수가 준다`() {
        val withdrawn = onboardedUser()
        val other = onboardedUser()
        val postId = createdPublicId(onboardedUser())
        val commentId = seedComment(onboardedUser(), postId)
        listOf(withdrawn, other).forEach { userId ->
            repost(postId, userId).andExpect(status().is2xxSuccessful)
            save(postId, userId).andExpect(status().is2xxSuccessful)
            like(postId, userId).andExpect(status().is2xxSuccessful)
            likeComment(commentId, userId).andExpect(status().is2xxSuccessful)
        }
        assertEquals(2, repostCountOf(postId))

        withdrawalUseCase.withdraw(withdrawn)

        assertEquals(1, repostCountOf(postId))
        assertEquals(1, postDetail(postId)["likeCount"])
        listOf("sns_reposts" to "reposter_id", "sns_saves" to "user_id", "sns_post_likes" to "user_id", "sns_comment_likes" to "user_id")
            .forEach { (table, column) ->
                assertEquals(0, count("select count(*) from $table where $column = ?", withdrawn), table)
                assertEquals(1, count("select count(*) from $table where $column = ?", other), table)
            }
    }

    @Test
    fun `탈퇴한 사용자가 건 차단과 뮤트는 지워지고 다른 사용자가 탈퇴한 사용자에게 건 차단과 뮤트는 남는다`() {
        val withdrawn = onboardedUser()
        val blocked = onboardedUser()
        val muted = onboardedUser()
        val blocker = onboardedUser()
        val muter = onboardedUser()
        relate(withdrawn, blocked, "block")
        relate(withdrawn, muted, "mute")
        relate(blocker, withdrawn, "block")
        relate(muter, withdrawn, "mute")

        withdrawalUseCase.withdraw(withdrawn)

        assertEquals(0, count("select count(*) from sns_blocks where blocker_id = ?", withdrawn))
        assertEquals(0, count("select count(*) from sns_mutes where muter_id = ?", withdrawn))
        assertEquals(1, count("select count(*) from sns_blocks where blocker_id = ? and blocked_id = ?", blocker, withdrawn))
        assertEquals(1, count("select count(*) from sns_mutes where muter_id = ? and muted_id = ?", muter, withdrawn))
    }

    @Test
    fun `탈퇴한 사용자의 게시물은 사유 WITHDRAWAL로 지워지고 태그 수가 줄며 미디어 파일은 커밋 뒤에 지워진다`() {
        val withdrawn = onboardedUser()
        val photoKey = tmpPhotoKey(withdrawn)
        val withPhoto = createdPublicId(withdrawn, postBody(photoKeys = listOf(photoKey), hashtags = listOf("산책")))
        val textOnly = createdPublicId(withdrawn, postBody(hashtags = listOf("산책", "간식")))
        val alreadyDeleted = createdPublicId(withdrawn)
        deletePost(alreadyDeleted, withdrawn).andExpect(status().isNoContent)
        createdPublicId(onboardedUser(), postBody(hashtags = listOf("산책")))

        withdrawalUseCase.withdraw(withdrawn)

        listOf(withPhoto, textOnly).forEach {
            val row = postRow(it)
            assertEquals("DELETED", row["status"])
            assertEquals("WITHDRAWAL", row["deletion_reason"])
            assertNull(row["caption"])
        }
        assertEquals("AUTHOR", postRow(alreadyDeleted)["deletion_reason"])
        assertEquals(mapOf("산책" to 1, "간식" to 0), hashtagCounts())
        assertTrue(promotedKeyOf(photoKey) in fakeFileStorage.deletedKeys)
        getPost(withPhoto).andExpect(status().isNotFound)
    }

    @Test
    fun `탈퇴한 사용자의 댓글은 사유 WITHDRAWAL로 지워지고 대댓글이 있는 것만 안내 문구로 남는다`() {
        val withdrawn = onboardedUser()
        val postId = createdPublicId(onboardedUser())
        val withReply = seedComment(withdrawn, postId)
        val reply = seedComment(onboardedUser(), postId, parentPublicId = withReply)
        val alone = seedComment(withdrawn, postId)
        val ownReply = seedComment(withdrawn, postId, parentPublicId = seedComment(onboardedUser(), postId))
        assertEquals(5, postDetail(postId)["commentCount"])

        withdrawalUseCase.withdraw(withdrawn)

        listOf(withReply, alone, ownReply).forEach {
            val row = commentRow(it)
            assertEquals("DELETED", row["status"])
            assertEquals("WITHDRAWAL", row["deletion_reason"])
        }
        assertEquals(2, postDetail(postId)["commentCount"])
        val items = commentPage(postId).feedItems().associateBy { it["publicId"] }
        assertEquals("WITHDRAWN", items.getValue(withReply)["status"])
        assertEquals("ACTIVE", items.getValue(reply)["status"])
        assertTrue(alone !in items && ownReply !in items)
    }

    private fun count(
        sql: String,
        vararg args: Any,
    ): Int = jdbcTemplate.queryForObject(sql, Int::class.java, *args)!!

    /** 통계 행의 (팔로워 수, 팔로잉 수) */
    private fun followCounts(userId: Long): Pair<Int, Int> =
        jdbcTemplate.queryForObject(
            "select follower_count, following_count from sns_user_stats where user_id = ?",
            { rs, _ -> rs.getInt(1) to rs.getInt(2) },
            userId,
        )!!
}
