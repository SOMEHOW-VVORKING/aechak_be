package com.aechak.api.sns

import com.aechak.domain.sns.comment.enums.CommentDeletionReason
import com.aechak.domain.support.Ulid
import com.jayway.jsonpath.JsonPath
import org.junit.jupiter.api.Test
import org.springframework.test.web.servlet.ResultActions
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull

/**
 * 통합 — 댓글 단건이 목록과 같은 규칙으로 댓글과 최상위 댓글을 판정하고 조립하는지.
 */
class CommentDetailIntegrationTest : SnsPostIntegrationTestBase() {
    @Test
    fun `최상위 댓글이면 parent가 null이다`() {
        val authorId = onboardedUser()
        val postId = createdPublicId(onboardedUser())
        val root = seedComment(authorId, postId, content = "산책 코스 어디예요?")

        val data = thread(root, onboardedUser())

        val comment = data["comment"] as Map<*, *>
        assertEquals(root, comment["publicId"])
        assertEquals("산책 코스 어디예요?", comment["content"])
        assertNull(data["parent"])
    }

    @Test
    fun `대댓글이면 parent가 최상위 댓글 항목이다`() {
        val postId = createdPublicId(onboardedUser())
        val root = seedComment(onboardedUser(), postId)
        val reply = seedComment(onboardedUser(), postId, parentPublicId = root)

        val data = thread(reply, onboardedUser())

        assertEquals(root, (data["comment"] as Map<*, *>)["parentPublicId"])
        assertEquals(root, (data["parent"] as Map<*, *>)["publicId"])
        assertEquals("ACTIVE", (data["parent"] as Map<*, *>)["status"])
        assertEquals(true, (data["comment"] as Map<*, *>)["replyable"])
    }

    @Test
    fun `지운 최상위 댓글 아래 대댓글의 parent는 안내 문구 항목이다`() {
        val postId = createdPublicId(onboardedUser())
        val root = seedComment(onboardedUser(), postId)
        val reply = seedComment(onboardedUser(), postId, parentPublicId = root)
        deleteCommentRow(root)

        val data = thread(reply, onboardedUser())

        val parent = data["parent"] as Map<*, *>
        assertEquals("DELETED", parent["status"])
        assertNull(parent["author"])
        assertNull(parent["content"])
        assertEquals(false, (data["comment"] as Map<*, *>)["replyable"])
    }

    @Test
    fun `지운 대댓글, 차단 관계 작성자의 댓글, 다른 사람에게 섀도우 처리된 댓글은 404 130700이다`() {
        val viewerId = onboardedUser()
        val postAuthorId = onboardedUser()
        val postId = createdPublicId(postAuthorId)
        val root = seedComment(onboardedUser(), postId)
        val deletedReply = seedComment(onboardedUser(), postId, parentPublicId = root, deletedBy = CommentDeletionReason.AUTHOR)
        val blockedId = onboardedUser()
        val byBlocked = seedComment(blockedId, postId)
        relate(viewerId, blockedId, "block")
        val shadowedId = onboardedUser()
        val byShadowed = seedComment(shadowedId, postId)
        relate(postAuthorId, shadowedId, "mute")

        listOf(deletedReply, byBlocked, byShadowed, Ulid.generate()).forEach { commentId ->
            expectError(getComment(commentId, viewerId), 404, 130700)
        }
        assertEquals(byShadowed, (thread(byShadowed, shadowedId)["comment"] as Map<*, *>)["publicId"])
    }

    @Test
    fun `지운 게시물의 댓글은 404 130400이다`() {
        val postAuthorId = onboardedUser()
        val postId = createdPublicId(postAuthorId)
        val commentId = seedComment(onboardedUser(), postId)
        deletePost(postId, postAuthorId)

        expectError(getComment(commentId, onboardedUser()), 404, 130400)
    }

    @Test
    fun `내가 차단한 작성자의 게시물에 달린 댓글은 403 130001이다`() {
        val viewerId = onboardedUser()
        val postAuthorId = onboardedUser()
        val postId = createdPublicId(postAuthorId)
        val commentId = seedComment(onboardedUser(), postId)
        relate(viewerId, postAuthorId, "block")

        expectError(getComment(commentId, viewerId), 403, 130001)
    }

    @Test
    fun `응답에 내부 id가 없다`() {
        val postId = createdPublicId(onboardedUser())
        val root = seedComment(onboardedUser(), postId)
        val reply = seedComment(onboardedUser(), postId, parentPublicId = root)

        val json =
            getComment(reply, onboardedUser())
                .andExpect(status().isOk)
                .andReturn()
                .response
                .getContentAsString(Charsets.UTF_8)

        assertEquals(setOf("comment", "parent"), JsonPath.read<Map<String, Any?>>(json, "$.data").keys)
        assertFalse(Regex("\"(id|parentId|authorId|postId)\"").containsMatchIn(json))
    }

    private fun thread(
        commentPublicId: String,
        viewerId: Long,
    ): Map<String, Any?> {
        val json =
            getComment(commentPublicId, viewerId)
                .andExpect(status().isOk)
                .andReturn()
                .response
                .getContentAsString(Charsets.UTF_8)
        return JsonPath.read(json, "$.data")
    }

    private fun expectError(
        result: ResultActions,
        httpStatus: Int,
        errorCode: Int,
    ) {
        result.andExpect(status().`is`(httpStatus)).andExpect(jsonPath("$.errorCode").value(errorCode))
    }
}
