package com.aechak.api.sns

import com.aechak.application.sns.usecase.SnsLikeUseCase
import com.aechak.application.sns.usecase.command.LikeCommentCommand
import com.aechak.domain.sns.comment.enums.CommentDeletionReason
import com.aechak.domain.support.Ulid
import com.jayway.jsonpath.JsonPath
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.test.web.servlet.ResultActions
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

/**
 * 통합: 댓글 좋아요 켜기와 끄기, 좋아요한 사람이 게시물 판정과 댓글 판정 뒤 멱등하게 동작하고 지금의 수를 돌려주며 댓글 항목의 수와 liked에 보이는지.
 * 깨지면 볼 수 없는 댓글이나 안내 문구만 남은 댓글의 좋아요가 바뀌거나 그 수가 응답에 실리거나, 반복 요청이 행을 겹치거나, 항목이 다른 댓글의 수를 싣는다.
 */
class CommentLikeIntegrationTest : SnsPostIntegrationTestBase() {
    @Autowired
    private lateinit var snsLikeUseCase: SnsLikeUseCase

    @Test
    fun `켜기는 201이고 응답에 지금의 좋아요 수만 싣으며 반복해도 행은 1개다`() {
        val commentId = seedComment(onboardedUser(), createdPublicId(onboardedUser()))
        val userId = onboardedUser()

        assertEquals(mapOf("likeCount" to 1), likeComment(commentId, userId).andExpect(status().isCreated).data())
        likeComment(commentId, userId)
            .andExpect(status().isCreated)
            .andExpect(jsonPath("$.data.likeCount").value(1))
        assertEquals(1, likeRowCount(userId, commentId))
    }

    @Test
    fun `끄기는 200이고 0을 돌려주며 다시 켜면 1이고 하지 않은 좋아요의 끄기는 다른 사람의 좋아요를 남긴다`() {
        val commentId = seedComment(onboardedUser(), createdPublicId(onboardedUser()))
        val userId = onboardedUser()
        likeComment(commentId, userId).andExpect(status().isCreated)

        assertEquals(mapOf("likeCount" to 0), unlikeComment(commentId, userId).andExpect(status().isOk).data())
        assertEquals(0, likeRowCount(userId, commentId))
        likeComment(commentId, userId)
            .andExpect(status().isCreated)
            .andExpect(jsonPath("$.data.likeCount").value(1))
        unlikeComment(commentId, onboardedUser())
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.likeCount").value(1))
    }

    @Test
    fun `내 댓글에도 좋아요할 수 있다`() {
        val authorId = onboardedUser()
        val commentId = seedComment(authorId, createdPublicId(onboardedUser()))

        likeComment(commentId, authorId)
            .andExpect(status().isCreated)
            .andExpect(jsonPath("$.data.likeCount").value(1))
    }

    @Test
    fun `게시물 작성자가 나를 차단했으면 130400이고 내가 게시물 작성자를 차단했으면 403 130001이다`() {
        val viewerId = onboardedUser()
        val blockingAuthor = onboardedUser()
        val onBlocking = seedComment(onboardedUser(), createdPublicId(blockingAuthor))
        relate(blockingAuthor, viewerId, "block")
        val blockedAuthor = onboardedUser()
        val onBlocked = seedComment(onboardedUser(), createdPublicId(blockedAuthor))
        relate(viewerId, blockedAuthor, "block")

        listOf(likeComment(onBlocking, viewerId), unlikeComment(onBlocking, viewerId)).forEach { expectError(it, 404, 130400) }
        listOf(likeComment(onBlocked, viewerId), unlikeComment(onBlocked, viewerId)).forEach { expectError(it, 403, 130001) }
    }

    @Test
    fun `댓글 작성자와 차단 관계이거나 내가 뮤트한 작성자의 댓글은 켜기와 끄기 모두 130700이고 좋아요가 남는다`() {
        val viewerId = onboardedUser()
        val postId = createdPublicId(onboardedUser())
        val blockedId = onboardedUser()
        val byBlocked = seedComment(blockedId, postId)
        val mutedId = onboardedUser()
        val byMuted = seedComment(mutedId, postId)
        likeComment(byBlocked, viewerId).andExpect(status().isCreated)
        relate(blockedId, viewerId, "block")
        relate(viewerId, mutedId, "mute")

        listOf(likeComment(byBlocked, viewerId), unlikeComment(byBlocked, viewerId), likeComment(byMuted, viewerId))
            .forEach { expectError(it, 404, 130700) }
        assertEquals(1, likeRowCount(viewerId, byBlocked), "볼 수 없게 된 댓글의 끄기는 막혀 좋아요가 남아야 한다")
    }

    @Test
    fun `지운 게시물의 댓글은 켜기와 끄기 모두 130400이다`() {
        val postAuthor = onboardedUser()
        val postId = createdPublicId(postAuthor)
        val commentId = seedComment(onboardedUser(), postId)
        deletePost(postId, postAuthor).andExpect(status().isNoContent)
        val userId = onboardedUser()

        listOf(likeComment(commentId, userId), unlikeComment(commentId, userId)).forEach { expectError(it, 404, 130400) }
    }

    @Test
    fun `안내 문구만 남은 최상위 댓글, 지운 대댓글, 없는 댓글은 130700이다`() {
        val postId = createdPublicId(onboardedUser())
        val root = seedComment(onboardedUser(), postId)
        seedComment(onboardedUser(), postId, parentPublicId = root)
        deleteCommentRow(root)
        val deletedReply = seedComment(onboardedUser(), postId, parentPublicId = seedComment(onboardedUser(), postId))
        deleteCommentRow(deletedReply, CommentDeletionReason.AUTHOR)
        val userId = onboardedUser()

        listOf(root, deletedReply, Ulid.generate()).forEach { expectError(likeComment(it, userId), 404, 130700) }
        expectError(unlikeComment(root, userId), 404, 130700)
    }

    @Test
    fun `섀도우 처리된 B는 자기 댓글 아래 다른 사람의 대댓글에 좋아요할 수 있다`() {
        val postAuthor = onboardedUser()
        val postId = createdPublicId(postAuthor)
        val shadowedId = onboardedUser()
        val root = seedComment(shadowedId, postId)
        val reply = seedComment(onboardedUser(), postId, parentPublicId = root)
        relate(postAuthor, shadowedId, "mute")

        likeComment(reply, shadowedId)
            .andExpect(status().isCreated)
            .andExpect(jsonPath("$.data.likeCount").value(1))
        expectError(likeComment(reply, onboardedUser()), 404, 130700)
    }

    @Test
    fun `목록, 단건, 수정 응답 항목이 그 댓글의 좋아요 수와 내 좋아요 여부를 싣고 비로그인은 liked가 false다`() {
        val viewerId = onboardedUser()
        val postId = createdPublicId(onboardedUser())
        val mine = seedComment(viewerId, postId)
        val other = seedComment(onboardedUser(), postId, parentPublicId = mine)
        likeComment(mine, viewerId).andExpect(status().isCreated)
        likeComment(mine, onboardedUser()).andExpect(status().isCreated)
        likeComment(other, onboardedUser()).andExpect(status().isCreated)

        val items = commentPage(postId, viewerId).feedItems().associateBy { it["publicId"] }
        assertEquals(2 to true, items.getValue(mine).let { it["likeCount"] to it["liked"] })
        assertEquals(1 to false, items.getValue(other).let { it["likeCount"] to it["liked"] })
        val guest = commentPage(postId).feedItems().associateBy { it["publicId"] }
        assertEquals(2 to false, guest.getValue(mine).let { it["likeCount"] to it["liked"] })

        val thread = getComment(other, viewerId).andExpect(status().isOk).data()
        assertEquals(1 to false, (thread["comment"] as Map<*, *>).let { it["likeCount"] to it["liked"] })
        assertEquals(2 to true, (thread["parent"] as Map<*, *>).let { it["likeCount"] to it["liked"] })

        updateComment(mine, viewerId)
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.likeCount").value(2))
            .andExpect(jsonPath("$.data.liked").value(true))
    }

    @Test
    fun `안내 문구 항목은 좋아요가 남아 있어도 0과 false다`() {
        val viewerId = onboardedUser()
        val postId = createdPublicId(onboardedUser())
        val root = seedComment(onboardedUser(), postId)
        seedComment(onboardedUser(), postId, parentPublicId = root)
        likeComment(root, viewerId).andExpect(status().isCreated)
        deleteCommentRow(root)

        val placeholder = commentPage(postId, viewerId).feedItems().single { it["publicId"] == root }
        assertEquals(0 to false, placeholder["likeCount"] to placeholder["liked"])
    }

    @Nested
    inner class Concurrency {
        @Test
        fun `같은 사용자의 동시 좋아요는 행 1개만 남긴다`() {
            val commentId = seedComment(onboardedUser(), createdPublicId(onboardedUser()))
            val userId = onboardedUser()

            val errors = runConcurrently(List(6) { { snsLikeUseCase.likeComment(LikeCommentCommand(userId, commentId)) } })

            assertTrue(errors.isEmpty(), "동시 좋아요에서 오류가 나면 안 된다: $errors")
            assertEquals(1, likeRowCount(userId, commentId))
        }
    }

    @Nested
    inner class Likers {
        @Test
        fun `24명 중 보는 사람과 차단 관계인 2명을 빼고 22명이며 likeCount는 24이고 hasMore는 false다`() {
            val viewerId = onboardedUser()
            val commentId = seedComment(onboardedUser(), createdPublicId(onboardedUser()))
            val likers = List(24) { onboardedUser() }
            likers.forEach { likeComment(commentId, it).andExpect(status().isCreated) }
            relate(viewerId, likers[3], "block")
            relate(likers[10], viewerId, "block")

            val data = commentLikers(commentId, viewerId).andExpect(status().isOk).data()

            assertEquals(24, data["likeCount"])
            assertEquals(false, data["hasMore"])
            assertEquals(
                (likers - likers[3] - likers[10]).reversed().map(::publicIdOf),
                data.items().map { it["publicId"] },
                "최근에 누른 순서여야 한다",
            )
        }

        @Test
        fun `본인 행은 isMe이고 행에 내부 id가 없다`() {
            val viewerId = onboardedUser()
            val commentId = seedComment(onboardedUser(), createdPublicId(onboardedUser()))
            likeComment(commentId, viewerId).andExpect(status().isCreated)

            val item =
                commentLikers(commentId, viewerId)
                    .andExpect(status().isOk)
                    .data()
                    .items()
                    .single()

            assertEquals(true, item["isMe"])
            assertEquals(false, item["following"])
            assertEquals(setOf("publicId", "nickname", "profileImageUrl", "pet", "isMe", "following"), item.keys)
        }

        @Test
        fun `안내 문구만 남은 댓글은 130700이고 지운 게시물의 댓글은 130400이다`() {
            val viewerId = onboardedUser()
            val postAuthor = onboardedUser()
            val postId = createdPublicId(postAuthor)
            val root = seedComment(onboardedUser(), postId)
            seedComment(onboardedUser(), postId, parentPublicId = root)
            deleteCommentRow(root)
            expectError(commentLikers(root, viewerId), 404, 130700)

            val alive = seedComment(onboardedUser(), postId)
            deletePost(postId, postAuthor).andExpect(status().isNoContent)
            expectError(commentLikers(alive, viewerId), 404, 130400)
        }
    }

    /** user가 이 댓글에 누른 좋아요 행 수 */
    private fun likeRowCount(
        userId: Long,
        commentPublicId: String,
    ): Int =
        jdbcTemplate.queryForObject(
            "select count(*) from sns_comment_likes l join sns_comments c on c.id = l.comment_id where l.user_id = ? and c.public_id = ?",
            Int::class.java,
            userId,
            commentPublicId,
        )!!

    private fun ResultActions.data(): Map<String, Any?> = JsonPath.read(andReturn().response.getContentAsString(Charsets.UTF_8), "$.data")

    @Suppress("UNCHECKED_CAST")
    private fun Map<String, Any?>.items(): List<Map<String, Any?>> = this["items"] as List<Map<String, Any?>>

    private fun expectError(
        result: ResultActions,
        httpStatus: Int,
        errorCode: Int,
    ) {
        result.andExpect(status().`is`(httpStatus)).andExpect(jsonPath("$.errorCode").value(errorCode))
    }
}
