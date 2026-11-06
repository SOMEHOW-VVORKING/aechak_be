package com.aechak.api.sns

import com.aechak.application.sns.commentdeletion.service.CommentDeletionService
import com.aechak.domain.sns.comment.enums.CommentDeletionReason
import com.aechak.domain.sns.comment.repository.CommentRepository
import com.aechak.domain.user.user.User
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import java.time.LocalDateTime
import kotlin.test.assertEquals

/**
 * 통합 — 상세와 피드 카드의 commentCount가 ACTIVE 댓글과 대댓글을 섀도우 규칙으로만 세고 차단과 일반 뮤트로는 줄지 않는지.
 */
class CommentCountIntegrationTest : SnsPostIntegrationTestBase() {
    @Autowired
    private lateinit var commentDeletionService: CommentDeletionService

    @Autowired
    private lateinit var commentRepository: CommentRepository

    @Test
    fun `댓글 10개 중 2개가 차단 관계 사용자의 것이면 수는 10이고 목록에는 8개다`() {
        val viewerId = onboardedUser()
        val blockedId = onboardedUser()
        val postId = createdPublicId(onboardedUser())
        repeat(8) { seedComment(onboardedUser(), postId) }
        repeat(2) { seedComment(blockedId, postId) }
        relate(viewerId, blockedId, "block")

        assertEquals(10, postDetail(postId, viewerId)["commentCount"])
        assertEquals(8, visibleCommentIds(postId, viewerId).size)
    }

    @Test
    fun `내가 뮤트한 사용자가 남의 게시물에 쓴 댓글은 수에 들어가고 목록에서는 빠진다`() {
        val viewerId = onboardedUser()
        val mutedId = onboardedUser()
        val postId = createdPublicId(onboardedUser())
        seedComment(mutedId, postId)
        relate(viewerId, mutedId, "mute")

        assertEquals(1, postDetail(postId, viewerId)["commentCount"])
        assertEquals(emptyList(), visibleCommentIds(postId, viewerId))
    }

    @Test
    fun `댓글과 대댓글이 하나씩 있어 수가 2인 게시물에서 댓글을 지우면 1이다`() {
        val userId = onboardedUser()
        val postId = createdPublicId(onboardedUser())
        val root = seedComment(userId, postId)
        seedComment(onboardedUser(), postId, parentPublicId = root)
        assertEquals(2, postDetail(postId)["commentCount"])

        deleteComment(root, userId)

        assertEquals(1, postDetail(postId)["commentCount"])
    }

    @Test
    fun `수가 5인 게시물에서 W의 댓글 둘을 탈퇴로 지우면 대댓글이 있는 것만 안내 문구로 남고 수는 3이다`() {
        val withdrawnId = onboardedUser()
        val postId = createdPublicId(onboardedUser())
        val withReply = seedComment(withdrawnId, postId)
        seedComment(onboardedUser(), postId, parentPublicId = withReply)
        val alone = seedComment(withdrawnId, postId)
        repeat(2) { seedComment(onboardedUser(), postId) }
        assertEquals(5, postDetail(postId)["commentCount"])

        tx.execute {
            listOf(
                withReply,
                alone,
            ).forEach { commentDeletionService.delete(commentRepository.findByPublicId(it)!!, CommentDeletionReason.WITHDRAWAL) }
            em.find(User::class.java, withdrawnId).withdraw()
        }

        assertEquals(3, postDetail(postId)["commentCount"])
        val items = commentPage(postId).feedItems()
        assertEquals("WITHDRAWN", items.single { it["publicId"] == withReply }["status"])
        assertEquals(4, items.size)
    }

    @Test
    fun `운영자가 지운 댓글은 수에서 빠진다`() {
        val postId = createdPublicId(onboardedUser())
        val commentId = seedComment(onboardedUser(), postId)
        seedComment(onboardedUser(), postId)

        deleteCommentRow(commentId, CommentDeletionReason.OPERATOR)

        assertEquals(1, postDetail(postId)["commentCount"])
    }

    @Test
    fun `이용 제한 사용자와 만료 전 임시 정지 사용자의 댓글은 수에 들어간다`() {
        val restrictedId = onboardedUser()
        val suspendedId = onboardedUser()
        val postId = createdPublicId(onboardedUser())
        seedComment(restrictedId, postId)
        seedComment(suspendedId, postId)
        tx.execute {
            em.find(User::class.java, restrictedId).restrict()
            em.find(User::class.java, suspendedId).suspendUntil(LocalDateTime.now().plusDays(7))
        }

        assertEquals(2, postDetail(postId, onboardedUser())["commentCount"])
    }

    @Test
    fun `비로그인은 섀도우 처리된 댓글을 뺀 공개 수를 받는다`() {
        val authorId = onboardedUser()
        val shadowedId = onboardedUser()
        val postId = createdPublicId(authorId)
        seedComment(onboardedUser(), postId)
        seedComment(shadowedId, postId)
        relate(authorId, shadowedId, "mute")

        assertEquals(1, postDetail(postId)["commentCount"])
        assertEquals(2, postDetail(postId, shadowedId)["commentCount"])
    }

    @Test
    fun `피드 카드의 댓글 수도 상세와 같다`() {
        val authorId = onboardedUser()
        val postId = createdPublicId(authorId)
        val root = seedComment(onboardedUser(), postId)
        seedComment(onboardedUser(), postId, parentPublicId = root)

        val card = feedPage("explore").feedItems().single { (it["post"] as Map<*, *>)["publicId"] == postId }

        assertEquals(2, (card["post"] as Map<*, *>)["commentCount"])
    }
}
