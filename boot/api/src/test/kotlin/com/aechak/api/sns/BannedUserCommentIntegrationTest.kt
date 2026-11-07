package com.aechak.api.sns

import com.aechak.domain.sns.error.SnsErrorCode
import com.aechak.domain.user.user.enums.UserStatus
import com.jayway.jsonpath.JsonPath
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.ResultActions
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

/**
 * 영구 정지 사용자의 대댓글은 빠지고 최상위 댓글은 다른 사람의 보이는 대댓글이 있을 때만 WITHDRAWN 안내 문구로 남는다.
 * 둘 다 댓글 수에서 빠지고 제재가 풀리면 다시 보인다.
 */
class BannedUserCommentIntegrationTest : SnsPostIntegrationTestBase() {
    private val viewer by lazy { onboardedUser() }
    private val banned by lazy { onboardedUser() }
    private val other by lazy { onboardedUser() }
    private val postId by lazy { createdPublicId(onboardedUser()) }

    /** 대댓글 없는 정지 사용자 최상위 댓글, 다른 사람 대댓글이 달린 정지 사용자 최상위 댓글, 다른 사람 최상위 댓글 아래 정지 사용자 대댓글 */
    private class Seeded(
        val lonelyRoot: String,
        val bannedRoot: String,
        val replyUnderBanned: String,
        val otherRoot: String,
        val bannedReply: String,
    )

    private fun seed(): Seeded {
        val bannedRoot = seedComment(banned, postId)
        val otherRoot = seedComment(other, postId)
        val seeded =
            Seeded(
                lonelyRoot = seedComment(banned, postId),
                bannedRoot = bannedRoot,
                replyUnderBanned = seedComment(other, postId, parentPublicId = bannedRoot),
                otherRoot = otherRoot,
                bannedReply = seedComment(banned, postId, parentPublicId = otherRoot),
            )
        setStatus(banned, UserStatus.BANNED)
        return seeded
    }

    @Test
    fun `목록에서 대댓글은 빠지고 대댓글이 남은 최상위 댓글만 WITHDRAWN이며 수에서 둘 다 빠진다`() {
        val s = seed()

        val items = commentPage(postId, viewer, size = 100).feedItems().associateBy { it["publicId"] }

        assertEquals(setOf(s.bannedRoot, s.replyUnderBanned, s.otherRoot), items.keys)
        val placeholder = items.getValue(s.bannedRoot)
        assertEquals("WITHDRAWN", placeholder["status"])
        assertNull(placeholder["author"])
        assertNull(placeholder["content"])
        assertEquals(false, items.getValue(s.replyUnderBanned)["replyable"])
        assertEquals(2, postDetail(postId, viewer)["commentCount"])
        assertEquals(2, postDetail(postId)["commentCount"], "비로그인 수도 같아야 한다")
    }

    @Test
    fun `정지 사용자의 대댓글과 대댓글 없는 최상위 댓글 단건은 404 130700이다`() {
        val s = seed()

        expectError(getComment(s.bannedReply, viewer), 404, SnsErrorCode.SNS_COMMENT_UNAVAILABLE.code)
        expectError(getComment(s.lonelyRoot, viewer), 404, SnsErrorCode.SNS_COMMENT_UNAVAILABLE.code)
        assertEquals("WITHDRAWN", commentStatusOf(getComment(s.replyUnderBanned, viewer)))
    }

    @Test
    fun `정지 사용자 댓글에는 좋아요와 신고가 130700이고 그 스레드에는 답글이 130705다`() {
        val s = seed()

        expectError(likeComment(s.bannedRoot, viewer), 404, SnsErrorCode.SNS_COMMENT_UNAVAILABLE.code)
        expectError(likeComment(s.bannedReply, viewer), 404, SnsErrorCode.SNS_COMMENT_UNAVAILABLE.code)
        expectError(report(viewer, s.bannedRoot), 404, SnsErrorCode.SNS_COMMENT_UNAVAILABLE.code)
        expectError(
            createComment(viewer, postId, commentBody(parentPublicId = s.bannedRoot)),
            409,
            SnsErrorCode.SNS_COMMENT_REPLY_CLOSED.code,
        )
        expectError(
            createComment(viewer, postId, commentBody(parentPublicId = s.replyUnderBanned)),
            409,
            SnsErrorCode.SNS_COMMENT_REPLY_CLOSED.code,
        )
        likeComment(s.replyUnderBanned, viewer).andExpect(status().is2xxSuccessful)
    }

    @Test
    fun `제재가 풀리면 댓글과 수가 돌아온다`() {
        val s = seed()
        setStatus(banned, UserStatus.ACTIVE)

        val items = commentPage(postId, viewer, size = 100).feedItems().associateBy { it["publicId"] }

        assertEquals(setOf(s.lonelyRoot, s.bannedRoot, s.replyUnderBanned, s.otherRoot, s.bannedReply), items.keys)
        assertEquals("ACTIVE", items.getValue(s.bannedRoot)["status"])
        assertEquals(5, postDetail(postId, viewer)["commentCount"])
    }

    private fun setStatus(
        userId: Long,
        status: UserStatus,
    ) {
        tx.execute {
            em
                .createQuery("update User u set u.status = :st, u.sanctionUntil = null where u.id = :id")
                .setParameter("st", status)
                .setParameter("id", userId)
                .executeUpdate()
        }
    }

    private fun report(
        reporterId: Long,
        commentPublicId: String,
    ): ResultActions =
        mockMvc.perform(
            post("/api/v1/reports")
                .header(HttpHeaders.AUTHORIZATION, "Bearer ${mintAccessToken(reporterId)}")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"targetType":"SNS_COMMENT","targetId":"$commentPublicId","reasonCode":"SPAM"}"""),
        )

    private fun commentStatusOf(result: ResultActions): String =
        JsonPath.read(
            result
                .andExpect(status().isOk)
                .andReturn()
                .response
                .getContentAsString(Charsets.UTF_8),
            "$.data.parent.status",
        )

    private fun expectError(
        result: ResultActions,
        httpStatus: Int,
        errorCode: Int,
    ) {
        result.andExpect(status().`is`(httpStatus)).andExpect(jsonPath("$.errorCode").value(errorCode))
    }
}
