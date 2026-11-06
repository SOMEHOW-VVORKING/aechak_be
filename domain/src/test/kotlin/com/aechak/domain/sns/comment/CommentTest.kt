package com.aechak.domain.sns.comment

import com.aechak.common.error.BusinessException
import com.aechak.domain.sns.comment.enums.CommentDeletionReason
import com.aechak.domain.sns.comment.enums.CommentStatus
import com.aechak.domain.sns.error.SnsErrorCode
import com.aechak.domain.sns.post.Post
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class CommentTest {
    private val post =
        Post.create(
            authorId = 1L,
            caption = "산책 다녀왔어요",
            photoKeys = emptyList(),
            video = null,
            hashtagIds = emptyList(),
            petId = null,
            idempotencyKey = "post-key",
        )

    private fun aComment(
        content: String = "코코 표정 너무 좋네요",
        parent: Comment? = null,
    ): Comment = Comment.write(post, parent, authorId = 2L, content = content, idempotencyKey = "key-1")

    @Test
    fun `내용을 NFC로 정규화하고 줄바꿈을 정리한 뒤 앞뒤 공백을 지운다`() {
        val comment = aComment(content = "  é\r\n산책  ")

        assertEquals("é\n산책", comment.content)
        assertEquals(CommentStatus.ACTIVE, comment.status)
    }

    @Test
    fun `공백만 있는 내용은 130702다`() {
        val e = assertFailsWith<BusinessException> { Comment.normalizeContent(" \r\n\t ") }

        assertEquals(SnsErrorCode.SNS_COMMENT_EMPTY, e.errorCode)
    }

    @Test
    fun `코드 포인트 500자까지 쓰고 501자는 130703이다`() {
        // 이모지는 UTF-16 두 칸이지만 코드 포인트 하나다
        assertEquals(500, Comment.normalizeContent("😀".repeat(500)).codePointCount(0, 1000))
        val e = assertFailsWith<BusinessException> { Comment.normalizeContent("😀".repeat(501)) }

        assertEquals(SnsErrorCode.SNS_COMMENT_TOO_LONG, e.errorCode)
    }

    @Test
    fun `결합 문자는 NFC로 합친 뒤 센다`() {
        // e와 결합 악센트 두 코드 포인트가 é 하나가 된다
        assertEquals("é".repeat(500), Comment.normalizeContent("é".repeat(500)))
    }

    @Test
    fun `같은 내용으로 고치면 false이고 수정 시각을 그대로 둔다`() {
        val comment = aComment()

        assertFalse(comment.edit("코코 표정 너무 좋네요"))
        assertNull(comment.editedAt)
    }

    @Test
    fun `정규화 뒤 같아지는 내용도 false다`() {
        val comment = aComment()

        assertFalse(comment.edit("  코코 표정 너무 좋네요\r\n"))
        assertNull(comment.editedAt)
    }

    @Test
    fun `다른 내용으로 고치면 true이고 내용과 수정 시각을 바꾼다`() {
        val comment = aComment()

        assertTrue(comment.edit("오늘도 귀엽네요"))
        assertEquals("오늘도 귀엽네요", comment.content)
        assertNotNull(comment.editedAt)
    }

    @Test
    fun `작성자 삭제와 운영자 삭제는 내용을 비우고 상태, 사유, 시각을 채운다`() {
        listOf(CommentDeletionReason.AUTHOR, CommentDeletionReason.OPERATOR).forEach { reason ->
            val comment = aComment()

            assertTrue(comment.delete(reason))
            assertEquals(CommentStatus.DELETED, comment.status)
            assertEquals(reason, comment.deletionReason)
            assertNotNull(comment.deletedAt)
            assertNull(comment.content)
        }
    }

    @Test
    fun `탈퇴 삭제는 내용을 남기고 상태, 사유, 시각을 채운다`() {
        val comment = aComment()

        assertTrue(comment.delete(CommentDeletionReason.WITHDRAWAL))
        assertEquals(CommentStatus.DELETED, comment.status)
        assertEquals(CommentDeletionReason.WITHDRAWAL, comment.deletionReason)
        assertNotNull(comment.deletedAt)
        assertEquals("코코 표정 너무 좋네요", comment.content)
    }

    @Test
    fun `이미 지운 댓글을 다시 지우면 false이고 아무것도 바꾸지 않는다`() {
        val comment = aComment()
        comment.delete(CommentDeletionReason.WITHDRAWAL)
        val deletedAt = comment.deletedAt

        assertFalse(comment.delete(CommentDeletionReason.AUTHOR))
        assertEquals(CommentDeletionReason.WITHDRAWAL, comment.deletionReason)
        assertSame(deletedAt, comment.deletedAt)
        assertEquals("코코 표정 너무 좋네요", comment.content)
    }

    @Test
    fun `최상위 댓글을 부모로 받고 대댓글은 부모로 받지 않는다`() {
        val root = aComment()
        val reply = aComment(parent = root)

        assertSame(root, reply.parent)
        assertFailsWith<IllegalArgumentException> { aComment(parent = reply) }
    }
}
