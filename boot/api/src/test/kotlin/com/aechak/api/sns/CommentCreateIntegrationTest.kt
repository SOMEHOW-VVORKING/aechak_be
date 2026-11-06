package com.aechak.api.sns

import com.aechak.domain.sns.comment.enums.CommentDeletionReason
import com.aechak.domain.support.Ulid
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.test.web.servlet.ResultActions
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * 통합 — 댓글 작성이 계약 순서대로 판정하고 같은 키의 재요청을 한 댓글로 묶으며 쓴 사람 기준 항목을 돌려주는지.
 */
class CommentCreateIntegrationTest : SnsPostIntegrationTestBase() {
    @Test
    fun `최상위 댓글을 쓰면 201과 쓴 사람 기준 항목이다`() {
        val userId = onboardedUser()
        val postId = createdPublicId(onboardedUser())

        val item = createdComment(userId, postId, commentBody(content = "  산책 코스 어디예요?\\r\\n"))

        assertEquals("ACTIVE", item["status"])
        assertNull(item["parentPublicId"])
        assertEquals("산책 코스 어디예요?", item["content"])
        assertEquals(publicIdOf(userId), (item["author"] as Map<*, *>)["publicId"])
        assertEquals(true, item["isMine"])
        assertEquals(true, item["replyable"])
        assertEquals(false, item["edited"])
        assertEquals(listOf(item["publicId"]), visibleCommentIds(postId))
    }

    @Test
    fun `같은 키로 두 번 보내면 행은 하나이고 같은 항목이다`() {
        val userId = onboardedUser()
        val postId = createdPublicId(onboardedUser())
        val key = Ulid.generate()

        val first = createdComment(userId, postId, idempotencyKey = key)
        val second = createdComment(userId, postId, commentBody(content = "다른 본문"), idempotencyKey = key)

        assertEquals(first, second)
        assertEquals(1, commentRowCount(postId))
    }

    @Test
    fun `대소문자만 다른 키는 다른 댓글이다`() {
        val userId = onboardedUser()
        val postId = createdPublicId(onboardedUser())

        createdComment(userId, postId, idempotencyKey = "key-a")
        createdComment(userId, postId, idempotencyKey = "KEY-A")

        assertEquals(2, commentRowCount(postId))
    }

    @Test
    fun `같은 문장을 새 키로 보내면 새 댓글이다`() {
        val userId = onboardedUser()
        val postId = createdPublicId(onboardedUser())

        val first = createdComment(userId, postId)
        val second = createdComment(userId, postId)

        assertNotEquals(first["publicId"], second["publicId"])
        assertEquals(2, commentRowCount(postId))
    }

    @Test
    fun `다른 게시물에서 같은 키를 쓰면 그 게시물의 새 댓글이다`() {
        val userId = onboardedUser()
        val postA = createdPublicId(onboardedUser())
        val postB = createdPublicId(onboardedUser())
        val key = Ulid.generate()

        val onA = createdComment(userId, postA, idempotencyKey = key)
        val onB = createdComment(userId, postB, idempotencyKey = key)

        assertNotEquals(onA["publicId"], onB["publicId"])
        assertEquals(1, commentRowCount(postB))
    }

    @Test
    fun `원래 댓글을 지운 뒤 같은 키로 다시 보내면 201과 DELETED 항목이고 새 행이 없다`() {
        val userId = onboardedUser()
        val postId = createdPublicId(onboardedUser())
        val key = Ulid.generate()
        val first = createdComment(userId, postId, idempotencyKey = key)
        deleteCommentRow(first["publicId"] as String, CommentDeletionReason.AUTHOR)

        val again = createdComment(userId, postId, idempotencyKey = key)

        assertEquals(first["publicId"], again["publicId"])
        assertEquals("DELETED", again["status"])
        assertNull(again["content"])
        assertNull(again["author"])
        assertEquals(1, commentRowCount(postId))
    }

    @Test
    fun `멱등키 헤더가 없으면 90001, 비었거나 101자면 90004다`() {
        val userId = onboardedUser()
        val postId = createdPublicId(onboardedUser())

        expectError(createComment(userId, postId, idempotencyKey = null), 400, 90001)
        expectError(createComment(userId, postId, idempotencyKey = " "), 400, 90004)
        expectError(createComment(userId, postId, idempotencyKey = "k".repeat(101)), 400, 90004)
        assertEquals(0, commentRowCount(postId))
    }

    @Test
    fun `content가 없거나 null이면 90001이다`() {
        val userId = onboardedUser()
        val postId = createdPublicId(onboardedUser())

        expectError(createComment(userId, postId, "{}"), 400, 90001)
        expectError(createComment(userId, postId, commentBody(content = null)), 400, 90001)
    }

    @Test
    fun `공백뿐인 내용은 130702, 500자를 넘으면 130703이다`() {
        val userId = onboardedUser()
        val postId = createdPublicId(onboardedUser())

        expectError(createComment(userId, postId, commentBody(content = "   ")), 400, 130702)
        expectError(createComment(userId, postId, commentBody(content = "가".repeat(501))), 400, 130703)
        assertEquals(0, commentRowCount(postId))
    }

    @Test
    fun `금지어가 든 긴 문장과 금지어 비율이 기준과 같은 문장은 가린 글로 저장한다`() {
        val userId = onboardedUser()
        val postId = createdPublicId(onboardedUser())

        val long = createdComment(userId, postId, commentBody(content = "시발 오늘 산책 정말 좋았어요"))
        val equal = createdComment(userId, postId, commentBody(content = "시발 산책"))

        assertEquals("** 오늘 산책 정말 좋았어요", long["content"])
        assertEquals("** 오늘 산책 정말 좋았어요", commentRow(long["publicId"] as String)["content"])
        assertEquals("** 산책", equal["content"])
    }

    @Test
    fun `금지어 비율이 기준을 넘으면 130704이고 행이 없다`() {
        val userId = onboardedUser()
        val postId = createdPublicId(onboardedUser())

        expectError(createComment(userId, postId, commentBody(content = "시발 씨발 좆!!!")), 400, 130704)
        assertEquals(0, commentRowCount(postId))
    }

    @Test
    fun `지운 게시물과 나를 차단한 작성자의 게시물은 404 130400이다`() {
        val userId = onboardedUser()
        val deletedAuthor = onboardedUser()
        val deleted = createdPublicId(deletedAuthor)
        deletePost(deleted, deletedAuthor)
        val blockingAuthor = onboardedUser()
        val blocking = createdPublicId(blockingAuthor)
        relate(blockingAuthor, userId, "block")

        expectError(createComment(userId, deleted), 404, 130400)
        expectError(createComment(userId, blocking), 404, 130400)
    }

    @Test
    fun `내가 차단한 작성자의 게시물은 403 130001이다`() {
        val userId = onboardedUser()
        val authorId = onboardedUser()
        val postId = createdPublicId(authorId)
        relate(userId, authorId, "block")

        expectError(createComment(userId, postId), 403, 130001)
        assertEquals(0, commentRowCount(postId))
    }

    @Test
    fun `게시물 작성자가 뮤트한 사용자의 댓글도 201이고 그 사람의 목록에만 보인다`() {
        val authorId = onboardedUser()
        val mutedId = onboardedUser()
        val postId = createdPublicId(authorId)
        relate(authorId, mutedId, "mute")

        val item = createdComment(mutedId, postId)

        assertEquals(listOf(item["publicId"]), visibleCommentIds(postId, mutedId))
        assertEquals(emptyList(), visibleCommentIds(postId, onboardedUser()))
        assertEquals(emptyList(), visibleCommentIds(postId, authorId))
    }

    @Nested
    inner class Concurrency {
        @Test
        fun `같은 키로 동시에 보내면 행은 하나이고 모두 같은 댓글을 받는다`() {
            val userId = onboardedUser()
            val postId = createdPublicId(onboardedUser())
            val key = Ulid.generate()
            val publicIds = java.util.Collections.synchronizedSet(mutableSetOf<Any?>())

            val errors = runConcurrently(List(4) { { publicIds += createdComment(userId, postId, idempotencyKey = key)["publicId"] } })

            assertTrue(errors.isEmpty(), errors.toString())
            assertEquals(1, publicIds.size)
            assertEquals(1, commentRowCount(postId))
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
