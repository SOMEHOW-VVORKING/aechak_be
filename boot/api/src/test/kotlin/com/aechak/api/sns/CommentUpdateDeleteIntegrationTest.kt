package com.aechak.api.sns

import com.aechak.application.sns.usecase.SnsCommentUseCase
import com.aechak.application.sns.usecase.command.UpdateCommentCommand
import com.aechak.common.error.BusinessException
import com.aechak.domain.sns.comment.enums.CommentDeletionReason
import com.aechak.domain.sns.error.SnsErrorCode
import com.aechak.domain.support.Ulid
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.test.web.servlet.ResultActions
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/**
 * 통합 — 본인 댓글 수정과 삭제가 계약 순서대로 판정하고 삭제가 게시물 판정 없이 상태와 내용을 정리하는지.
 */
class CommentUpdateDeleteIntegrationTest : SnsPostIntegrationTestBase() {
    @Autowired
    private lateinit var snsCommentUseCase: SnsCommentUseCase

    @Test
    fun `수정하면 200과 바뀐 내용이고 edited가 true다`() {
        val userId = onboardedUser()
        val postId = createdPublicId(onboardedUser())
        val commentId = seedComment(userId, postId, content = "처음 쓴 글")

        updateComment(commentId, userId, commentBody(content = "고친 글"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.content").value("고친 글"))
            .andExpect(jsonPath("$.data.edited").value(true))
            .andExpect(jsonPath("$.data.isMine").value(true))
        assertNotNull(commentRow(commentId)["edited_at"])
    }

    @Test
    fun `같은 내용으로 저장하면 edited가 그대로 false다`() {
        val userId = onboardedUser()
        val postId = createdPublicId(onboardedUser())
        val commentId = seedComment(userId, postId, content = "처음 쓴 글")

        updateComment(commentId, userId, commentBody(content = " 처음 쓴 글 "))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.edited").value(false))
        assertNull(commentRow(commentId)["edited_at"])
    }

    @Test
    fun `대댓글을 고치면 응답 parentPublicId가 최상위 댓글이다`() {
        val userId = onboardedUser()
        val postId = createdPublicId(onboardedUser())
        val root = seedComment(onboardedUser(), postId)
        val reply = seedComment(userId, postId, parentPublicId = root)

        updateComment(reply, userId)
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.parentPublicId").value(root))
    }

    @Test
    fun `수정에도 금지어 가리기와 130704, 130702, 130703이 적용된다`() {
        val userId = onboardedUser()
        val postId = createdPublicId(onboardedUser())
        val commentId = seedComment(userId, postId, content = "처음 쓴 글")

        updateComment(commentId, userId, commentBody(content = "시발 산책"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.content").value("** 산책"))
        expectError(updateComment(commentId, userId, commentBody(content = "시발 씨발 좆!!!")), 400, 130704)
        expectError(updateComment(commentId, userId, commentBody(content = " ")), 400, 130702)
        expectError(updateComment(commentId, userId, commentBody(content = "가".repeat(501))), 400, 130703)
        assertEquals("** 산책", commentRow(commentId)["content"])
    }

    @Test
    fun `content가 없으면 90001이다`() {
        val userId = onboardedUser()
        val commentId = seedComment(userId, createdPublicId(onboardedUser()))

        expectError(updateComment(commentId, userId, "{}"), 400, 90001)
    }

    @Test
    fun `남의 댓글 수정과 삭제는 403 130701이다`() {
        val postId = createdPublicId(onboardedUser())
        val commentId = seedComment(onboardedUser(), postId)
        val otherId = onboardedUser()

        expectError(updateComment(commentId, otherId), 403, 130701)
        expectError(deleteComment(commentId, otherId), 403, 130701)
        assertEquals("ACTIVE", commentRow(commentId)["status"])
    }

    @Test
    fun `게시물 작성자도 남의 댓글을 지우면 403 130701이다`() {
        val postAuthorId = onboardedUser()
        val postId = createdPublicId(postAuthorId)
        val commentId = seedComment(onboardedUser(), postId)

        expectError(deleteComment(commentId, postAuthorId), 403, 130701)
        assertEquals("ACTIVE", commentRow(commentId)["status"])
    }

    @Test
    fun `지운 댓글의 수정은 404 130700이고 상태가 그대로다`() {
        val userId = onboardedUser()
        val commentId = seedComment(userId, createdPublicId(onboardedUser()), deletedBy = CommentDeletionReason.AUTHOR)

        expectError(updateComment(commentId, userId), 404, 130700)
        assertEquals("DELETED", commentRow(commentId)["status"])
        assertNull(commentRow(commentId)["content"])
    }

    @Test
    fun `지운 게시물의 댓글 수정은 404 130400이다`() {
        val userId = onboardedUser()
        val postAuthorId = onboardedUser()
        val postId = createdPublicId(postAuthorId)
        val commentId = seedComment(userId, postId)
        deletePost(postId, postAuthorId)

        expectError(updateComment(commentId, userId), 404, 130400)
    }

    @Test
    fun `내가 차단한 게시물 작성자의 게시물에 단 내 댓글 수정은 403 130001이다`() {
        val userId = onboardedUser()
        val postAuthorId = onboardedUser()
        val postId = createdPublicId(postAuthorId)
        val commentId = seedComment(userId, postId)
        relate(userId, postAuthorId, "block")

        expectError(updateComment(commentId, userId), 403, 130001)
    }

    @Test
    fun `없는 댓글에 빈 내용을 보내면 404가 아니라 400 130702다`() {
        expectError(updateComment(Ulid.generate(), onboardedUser(), commentBody(content = " ")), 400, 130702)
    }

    @Test
    fun `없는 댓글의 수정과 삭제는 404 130700이다`() {
        val userId = onboardedUser()

        expectError(updateComment(Ulid.generate(), userId), 404, 130700)
        expectError(deleteComment("not-a-ulid", userId), 404, 130700)
    }

    @Test
    fun `삭제하면 204이고 상태, 사유, 시각을 남기고 내용을 비운다`() {
        val userId = onboardedUser()
        val commentId = seedComment(userId, createdPublicId(onboardedUser()))

        deleteComment(commentId, userId).andExpect(status().isNoContent)

        val row = commentRow(commentId)
        assertEquals("DELETED", row["status"])
        assertEquals("AUTHOR", row["deletion_reason"])
        assertNotNull(row["deleted_at"])
        assertNull(row["content"])
    }

    @Test
    fun `이미 지운 댓글을 다시 지워도 204이고 처음 지운 시각이 그대로다`() {
        val userId = onboardedUser()
        val commentId = seedComment(userId, createdPublicId(onboardedUser()))
        deleteComment(commentId, userId).andExpect(status().isNoContent)
        val deletedAt = commentRow(commentId)["deleted_at"]

        deleteComment(commentId, userId).andExpect(status().isNoContent)

        assertEquals(deletedAt, commentRow(commentId)["deleted_at"])
    }

    @Test
    fun `지운 남의 댓글을 지워도 403 130701이다`() {
        val commentId = seedComment(onboardedUser(), createdPublicId(onboardedUser()), deletedBy = CommentDeletionReason.AUTHOR)

        expectError(deleteComment(commentId, onboardedUser()), 403, 130701)
    }

    @Test
    fun `지운 게시물과 차단 관계인 작성자의 게시물에 단 내 댓글도 204로 지운다`() {
        val userId = onboardedUser()
        val deletedAuthor = onboardedUser()
        val deletedPost = createdPublicId(deletedAuthor)
        val onDeleted = seedComment(userId, deletedPost)
        deletePost(deletedPost, deletedAuthor)
        val blockingAuthor = onboardedUser()
        val onBlocking = seedComment(userId, createdPublicId(blockingAuthor))
        relate(blockingAuthor, userId, "block")
        val blockedAuthor = onboardedUser()
        val onBlocked = seedComment(userId, createdPublicId(blockedAuthor))
        relate(userId, blockedAuthor, "block")

        listOf(onDeleted, onBlocking, onBlocked).forEach { commentId ->
            deleteComment(commentId, userId).andExpect(status().isNoContent)
            assertEquals("DELETED", commentRow(commentId)["status"])
        }
    }

    @Nested
    inner class Concurrency {
        @Test
        fun `같은 댓글의 수정은 삭제의 잠금이 풀린 뒤 실행하고 삭제가 먼저 끝나면 130700이다`() {
            val userId = onboardedUser()
            val commentId = seedComment(userId, createdPublicId(onboardedUser()))
            val pool = Executors.newSingleThreadExecutor()
            try {
                val later =
                    tx.execute {
                        // 삭제 요청처럼 댓글 행을 잠그고 지운 채 커밋을 미룬다
                        jdbcTemplate.queryForList("select id from sns_comments where public_id = ? for update", commentId)
                        jdbcTemplate.update(
                            "update sns_comments set status = 'DELETED', deletion_reason = 'AUTHOR', deleted_at = now(6), content = null where public_id = ?",
                            commentId,
                        )
                        val future = pool.submit { snsCommentUseCase.updateComment(UpdateCommentCommand(userId, commentId, "고친 글")) }
                        assertFailsWith<TimeoutException> { future.get(1, TimeUnit.SECONDS) }
                        future
                    }!!
                val error = assertFailsWith<java.util.concurrent.ExecutionException> { later.get(30, TimeUnit.SECONDS) }.cause
                assertEquals(SnsErrorCode.SNS_COMMENT_UNAVAILABLE, (error as BusinessException).errorCode)
            } finally {
                pool.shutdownNow()
            }
            assertNull(commentRow(commentId)["content"])
        }
    }

    private fun expectError(
        result: ResultActions,
        httpStatus: Int,
        errorCode: Int,
    ) {
        result.andExpect(status().`is`(httpStatus)).andExpect(jsonPath("$.errorCode").value(errorCode))
    }
}
