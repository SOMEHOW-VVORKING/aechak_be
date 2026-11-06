package com.aechak.api.report

import com.aechak.api.sns.SnsPostIntegrationTestBase
import com.aechak.common.error.CommonErrorCode
import com.aechak.domain.review.review.Review
import com.aechak.domain.sns.comment.enums.CommentDeletionReason
import com.aechak.domain.sns.error.SnsErrorCode
import com.aechak.domain.sns.report.CommentReport
import com.aechak.domain.sns.report.PostReport
import com.aechak.domain.sns.report.enums.PostReportReason
import com.aechak.domain.sns.report.event.CommentReportReceivedEvent
import com.aechak.domain.sns.report.event.PostReportReceivedEvent
import com.aechak.domain.support.Ulid
import com.aechak.domain.user.error.UserErrorCode
import com.aechak.domain.user.report.UserReport
import com.aechak.domain.user.report.enums.ReportReasonCode
import com.aechak.domain.user.report.event.UserReportReceivedEvent
import com.aechak.domain.user.user.User
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.test.context.event.ApplicationEvents
import org.springframework.test.context.event.RecordApplicationEvents
import org.springframework.test.web.servlet.ResultActions
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

/**
 * 통합 테스트. POST /reports의 SNS 대상이 신고자가 볼 수 있는 대상만 접수하고 공통 신고 규칙(자기 신고, 중복, 기타 사유)을 지키는지 HTTP부터 고정한다.
 * 깨지면 볼 수 없는 게시물이 신고되거나 차단 관계가 다른 코드로 드러난다.
 */
@RecordApplicationEvents
class SnsReportIntegrationTest : SnsPostIntegrationTestBase() {
    @Autowired
    private lateinit var applicationEvents: ApplicationEvents

    // ---------- 게시물 ----------

    @Test
    fun `타인 게시물을 신고하면 201을 반환하고 신고를 저장하며 접수 이벤트를 한 번 발행한다`() {
        val authorId = onboardedUser()
        val reporterId = onboardedUser()
        val publicId = createdPublicId(authorId)

        postReport(reporterId, reportJson("SNS_POST", publicId, "OTHER", "  광고 계정 같아요  "))
            .andExpect(status().isCreated)

        val report = postReports().single()
        assertEquals(postIdOf(publicId), report.postId)
        assertEquals(reporterId, report.reporterId)
        assertEquals(PostReportReason.OTHER, report.reasonCode)
        assertEquals("광고 계정 같아요", report.reasonText)
        val event = recorded<PostReportReceivedEvent>().single()
        assertEquals(report.id, event.reportId)
        assertEquals(publicId, event.postPublicId)
    }

    @Test
    fun `게시물 신고가 기타 사유인데 상세 내용이 공백뿐이면 400과 130801을 반환한다`() {
        val publicId = createdPublicId(onboardedUser())

        postReport(onboardedUser(), reportJson("SNS_POST", publicId, "OTHER", " \\n "))
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.errorCode").value(SnsErrorCode.SNS_POST_REPORT_REASON_TEXT_REQUIRED.code))
        assertNoPostReport()
    }

    @Test
    fun `본인 게시물을 신고하면 400과 130800을 반환한다`() {
        val authorId = onboardedUser()
        val publicId = createdPublicId(authorId)

        postReport(authorId, reportJson("SNS_POST", publicId, "SPAM"))
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.errorCode").value(SnsErrorCode.SNS_POST_REPORT_SELF.code))
        assertNoPostReport()
    }

    @Test
    fun `없거나 삭제된 게시물, 탈퇴한 작성자의 게시물을 신고하면 404와 130400을 반환한다`() {
        val authorId = onboardedUser()
        val reporterId = onboardedUser()
        val deleted = createdPublicId(authorId)
        deletePost(deleted, authorId).andExpect(status().isNoContent)
        val withdrawnAuthorId = onboardedUser()
        val ofWithdrawn = createdPublicId(withdrawnAuthorId)
        tx.execute { em.find(User::class.java, withdrawnAuthorId).withdraw() }

        listOf("01JUNKNOWNPOST0000000000000", deleted, ofWithdrawn).forEach { publicId ->
            postReport(reporterId, reportJson("SNS_POST", publicId, "SPAM"))
                .andExpect(status().isNotFound)
                .andExpect(jsonPath("$.errorCode").value(SnsErrorCode.SNS_POST_UNAVAILABLE.code))
        }
        assertNoPostReport()
    }

    @Test
    fun `나를 차단한 작성자의 게시물은 삭제된 게시물과 같은 404와 130400이다`() {
        val authorId = onboardedUser()
        val reporterId = onboardedUser()
        val publicId = createdPublicId(authorId)
        relate(authorId, reporterId, "block")

        postReport(reporterId, reportJson("SNS_POST", publicId, "SPAM"))
            .andExpect(status().isNotFound)
            .andExpect(jsonPath("$.errorCode").value(SnsErrorCode.SNS_POST_UNAVAILABLE.code))
        assertNoPostReport()
    }

    @Test
    fun `내가 차단한 작성자의 게시물은 상세와 같은 403과 130001이다`() {
        val authorId = onboardedUser()
        val reporterId = onboardedUser()
        val publicId = createdPublicId(authorId)
        relate(reporterId, authorId, "block")

        postReport(reporterId, reportJson("SNS_POST", publicId, "SPAM"))
            .andExpect(status().isForbidden)
            .andExpect(jsonPath("$.errorCode").value(SnsErrorCode.SNS_USER_BLOCKED_BY_VIEWER.code))
        assertNoPostReport()
    }

    @Test
    fun `뮤트한 작성자의 게시물은 신고할 수 있다`() {
        val authorId = onboardedUser()
        val reporterId = onboardedUser()
        val publicId = createdPublicId(authorId)
        relate(reporterId, authorId, "mute")

        postReport(reporterId, reportJson("SNS_POST", publicId, "ABUSE")).andExpect(status().isCreated)
        assertEquals(1, postReports().size)
    }

    @Test
    fun `같은 게시물을 다시 신고하면 409와 130802를 반환하고 신고와 이벤트는 하나만 남는다`() {
        val publicId = createdPublicId(onboardedUser())
        val reporterId = onboardedUser()

        postReport(reporterId, reportJson("SNS_POST", publicId, "SPAM")).andExpect(status().isCreated)
        postReport(reporterId, reportJson("SNS_POST", publicId, "FRAUD"))
            .andExpect(status().isConflict)
            .andExpect(jsonPath("$.errorCode").value(SnsErrorCode.SNS_POST_ALREADY_REPORTED.code))

        assertEquals(1, postReports().size)
        assertEquals(1, recorded<PostReportReceivedEvent>().size)
    }

    @Test
    fun `다른 신고자가 같은 게시물을 신고하면 각각 접수한다`() {
        val publicId = createdPublicId(onboardedUser())

        postReport(onboardedUser(), reportJson("SNS_POST", publicId, "SPAM")).andExpect(status().isCreated)
        postReport(onboardedUser(), reportJson("SNS_POST", publicId, "SPAM")).andExpect(status().isCreated)

        assertEquals(2, postReports().size)
    }

    @Test
    fun `게시물 신고에 SNS 사유가 아닌 코드를 보내면 400과 INVALID_REQUEST를 반환한다`() {
        val publicId = createdPublicId(onboardedUser())

        postReport(onboardedUser(), reportJson("SNS_POST", publicId, "FALSE_AD"))
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.errorCode").value(CommonErrorCode.INVALID_REQUEST.code))
        assertNoPostReport()
    }

    // ---------- 댓글 ----------

    @Test
    fun `타인 대댓글을 신고하면 201을 반환하고 그 대댓글만 신고로 저장하며 접수 이벤트를 한 번 발행한다`() {
        val postPublicId = createdPublicId(onboardedUser())
        val root = seedComment(onboardedUser(), postPublicId)
        val reply = seedComment(onboardedUser(), postPublicId, parentPublicId = root)
        val reporterId = onboardedUser()

        postReport(reporterId, reportJson("SNS_COMMENT", reply, "OTHER", "  욕설이에요  ")).andExpect(status().isCreated)

        val report = commentReports().single()
        assertEquals(commentIdOf(reply), report.commentId)
        assertEquals(reporterId, report.reporterId)
        assertEquals(PostReportReason.OTHER, report.reasonCode)
        assertEquals("욕설이에요", report.reasonText)
        val event = recorded<CommentReportReceivedEvent>().single()
        assertEquals(report.id, event.reportId)
        assertEquals(reply, event.commentPublicId)
        assertEquals(postPublicId, event.postPublicId)
    }

    @Test
    fun `본인 댓글을 신고하면 400과 130803을 반환한다`() {
        val authorId = onboardedUser()
        val comment = seedComment(authorId, createdPublicId(onboardedUser()))

        expectError(postReport(authorId, reportJson("SNS_COMMENT", comment, "SPAM")), 400, SnsErrorCode.SNS_COMMENT_REPORT_SELF.code)
        assertNoCommentReport()
    }

    @Test
    fun `댓글 신고가 기타 사유인데 상세 내용이 없으면 400과 130801을 반환한다`() {
        val comment = seedComment(onboardedUser(), createdPublicId(onboardedUser()))

        expectError(
            postReport(onboardedUser(), reportJson("SNS_COMMENT", comment, "OTHER", "   ")),
            400,
            SnsErrorCode.SNS_POST_REPORT_REASON_TEXT_REQUIRED.code,
        )
        assertNoCommentReport()
    }

    @Test
    fun `같은 댓글을 다시 신고하면 409와 130804를 반환하고 다른 신고자는 각각 접수한다`() {
        val comment = seedComment(onboardedUser(), createdPublicId(onboardedUser()))
        val reporterId = onboardedUser()

        postReport(reporterId, reportJson("SNS_COMMENT", comment, "SPAM")).andExpect(status().isCreated)
        expectError(
            postReport(reporterId, reportJson("SNS_COMMENT", comment, "ABUSE")),
            409,
            SnsErrorCode.SNS_COMMENT_ALREADY_REPORTED.code,
        )
        postReport(onboardedUser(), reportJson("SNS_COMMENT", comment, "SPAM")).andExpect(status().isCreated)

        assertEquals(2, commentReports().size)
        assertEquals(2, recorded<CommentReportReceivedEvent>().size)
    }

    @Test
    fun `지운 게시물의 댓글과 나를 차단한 게시물 작성자의 댓글은 130400, 내가 차단한 게시물 작성자의 댓글은 403 130001이다`() {
        val reporterId = onboardedUser()
        val deletedPostAuthor = onboardedUser()
        val deletedPost = createdPublicId(deletedPostAuthor)
        val onDeleted = seedComment(onboardedUser(), deletedPost)
        deletePost(deletedPost, deletedPostAuthor).andExpect(status().isNoContent)
        val blockingAuthor = onboardedUser()
        val onBlocking = seedComment(onboardedUser(), createdPublicId(blockingAuthor))
        relate(blockingAuthor, reporterId, "block")
        val blockedAuthor = onboardedUser()
        val onBlocked = seedComment(onboardedUser(), createdPublicId(blockedAuthor))
        relate(reporterId, blockedAuthor, "block")

        listOf(onDeleted, onBlocking).forEach {
            expectError(postReport(reporterId, reportJson("SNS_COMMENT", it, "SPAM")), 404, SnsErrorCode.SNS_POST_UNAVAILABLE.code)
        }
        expectError(postReport(reporterId, reportJson("SNS_COMMENT", onBlocked, "SPAM")), 403, SnsErrorCode.SNS_USER_BLOCKED_BY_VIEWER.code)
        assertNoCommentReport()
    }

    @Test
    fun `없는 댓글, 지운 댓글, 안내 문구만 남은 댓글, 차단하거나 뮤트한 작성자의 댓글은 모두 404와 130700이다`() {
        val reporterId = onboardedUser()
        val postPublicId = createdPublicId(onboardedUser())
        val placeholder = seedComment(onboardedUser(), postPublicId)
        seedComment(onboardedUser(), postPublicId, parentPublicId = placeholder)
        deleteCommentRow(placeholder)
        val deleted = seedComment(onboardedUser(), postPublicId, deletedBy = CommentDeletionReason.OPERATOR)
        val blockingId = onboardedUser()
        val byBlocking = seedComment(blockingId, postPublicId)
        relate(blockingId, reporterId, "block")
        val blockedId = onboardedUser()
        val byBlocked = seedComment(blockedId, postPublicId)
        relate(reporterId, blockedId, "block")
        val mutedId = onboardedUser()
        val byMuted = seedComment(mutedId, postPublicId)
        relate(reporterId, mutedId, "mute")

        listOf(Ulid.generate(), placeholder, deleted, byBlocking, byBlocked, byMuted).forEach {
            expectError(postReport(reporterId, reportJson("SNS_COMMENT", it, "SPAM")), 404, SnsErrorCode.SNS_COMMENT_UNAVAILABLE.code)
        }
        assertNoCommentReport()
    }

    @Test
    fun `섀도우 처리된 댓글 아래 대댓글은 제3자에게 130700이고 섀도우 처리된 작성자 본인은 신고할 수 있다`() {
        val postAuthor = onboardedUser()
        val postPublicId = createdPublicId(postAuthor)
        val shadowedId = onboardedUser()
        val root = seedComment(shadowedId, postPublicId)
        val reply = seedComment(onboardedUser(), postPublicId, parentPublicId = root)
        relate(postAuthor, shadowedId, "mute")

        expectError(postReport(onboardedUser(), reportJson("SNS_COMMENT", reply, "SPAM")), 404, SnsErrorCode.SNS_COMMENT_UNAVAILABLE.code)
        postReport(shadowedId, reportJson("SNS_COMMENT", reply, "SPAM")).andExpect(status().isCreated)
        assertEquals(1, commentReports().size)
    }

    // ---------- 프로필 유저 ----------

    @Test
    fun `SNS 프로필의 사용자를 publicId로 신고하면 사용자 신고로 저장하고 근거 리뷰 없이 접수 이벤트를 발행한다`() {
        val targetId = onboardedUser()
        val reporterId = onboardedUser()

        postReport(reporterId, reportJson("SNS_USER", publicIdOf(targetId), "ABUSE"))
            .andExpect(status().isCreated)

        val report = userReports().single()
        assertEquals(reporterId, report.reporter.id)
        assertEquals(targetId, report.targetUser.id)
        assertEquals(ReportReasonCode.ABUSE, report.reasonCode)
        val event = recorded<UserReportReceivedEvent>().single()
        assertEquals(targetId, event.targetUserId)
        assertNull(event.reviewId)
    }

    @Test
    fun `차단 관계와 상관없이 사용자를 신고할 수 있다`() {
        val reporterId = onboardedUser()
        val blockingMe = onboardedUser()
        val blockedByMe = onboardedUser()
        relate(blockingMe, reporterId, "block")
        relate(reporterId, blockedByMe, "block")

        postReport(reporterId, reportJson("SNS_USER", publicIdOf(blockingMe), "SPAM")).andExpect(status().isCreated)
        postReport(reporterId, reportJson("SNS_USER", publicIdOf(blockedByMe), "SPAM")).andExpect(status().isCreated)

        assertEquals(setOf(blockingMe, blockedByMe), userReports().map { it.targetUser.id }.toSet())
    }

    @Test
    fun `자기 자신을 신고하면 400과 30200을 반환한다`() {
        val userId = onboardedUser()

        postReport(userId, reportJson("SNS_USER", publicIdOf(userId), "SPAM"))
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.errorCode").value(UserErrorCode.SELF_REPORT_NOT_ALLOWED.code))
        assertTrue(userReports().isEmpty())
    }

    @Test
    fun `없거나 탈퇴했거나 온보딩 전인 사용자를 신고하면 404와 130000을 반환한다`() {
        val reporterId = onboardedUser()
        val withdrawnId = onboardedUser()
        tx.execute { em.find(User::class.java, withdrawnId).withdraw() }
        val pendingId =
            tx.execute {
                User
                    .preRegister()
                    .also { em.persist(it) }
                    .also { em.flush() }
                    .id
            }!!

        listOf("01JUNKNOWNUSER0000000000000", publicIdOf(withdrawnId), publicIdOf(pendingId)).forEach { publicId ->
            postReport(reporterId, reportJson("SNS_USER", publicId, "SPAM"))
                .andExpect(status().isNotFound)
                .andExpect(jsonPath("$.errorCode").value(SnsErrorCode.SNS_USER_UNAVAILABLE.code))
        }
        assertTrue(userReports().isEmpty())
    }

    @Test
    fun `리뷰 경로로 이미 신고한 사용자를 SNS에서 다시 신고하면 409와 30202를 반환한다`() {
        val targetId = onboardedUser()
        val reporterId = onboardedUser()
        val reviewId =
            tx.execute {
                Review
                    .write(
                        productId = 1L,
                        optionNameSnapshot = "기본",
                        orderItemId = 1L,
                        authorUserId = targetId,
                        rating = 5,
                        content = "좋은 상품입니다",
                    ).also { em.persist(it) }
                    .id
            }!!
        postReport(reporterId, reportJson("USER", reviewId.toString(), "SPAM")).andExpect(status().isCreated)

        postReport(reporterId, reportJson("SNS_USER", publicIdOf(targetId), "ABUSE"))
            .andExpect(status().isConflict)
            .andExpect(jsonPath("$.errorCode").value(UserErrorCode.USER_ALREADY_REPORTED.code))
        assertEquals(1, userReports().size)
    }

    @Test
    fun `사용자 신고도 기타 사유면 상세 내용이 있어야 해서 400과 30201을 반환한다`() {
        postReport(onboardedUser(), reportJson("SNS_USER", publicIdOf(onboardedUser()), "OTHER", "   "))
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.errorCode").value(UserErrorCode.REPORT_REASON_REQUIRED.code))
        assertTrue(userReports().isEmpty())
    }

    private fun postReport(
        reporterId: Long,
        body: String,
    ): ResultActions =
        mockMvc.perform(
            post("/api/v1/reports")
                .header(HttpHeaders.AUTHORIZATION, "Bearer ${mintAccessToken(reporterId)}")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body),
        )

    private fun reportJson(
        targetType: String,
        targetId: String,
        reasonCode: String,
        reasonText: String? = null,
    ): String =
        listOfNotNull(
            "\"targetType\":\"$targetType\"",
            "\"targetId\":\"$targetId\"",
            "\"reasonCode\":\"$reasonCode\"",
            reasonText?.let { "\"reasonText\":\"$it\"" },
        ).joinToString(separator = ",", prefix = "{", postfix = "}")

    private fun postIdOf(publicId: String): Long =
        jdbcTemplate.queryForObject("select id from sns_posts where public_id = ?", Long::class.java, publicId)!!

    private fun commentIdOf(publicId: String): Long =
        jdbcTemplate.queryForObject("select id from sns_comments where public_id = ?", Long::class.java, publicId)!!

    private fun commentReports(): List<CommentReport> =
        tx.execute { em.createQuery("select r from CommentReport r order by r.id", CommentReport::class.java).resultList }!!

    private fun assertNoCommentReport() {
        assertTrue(commentReports().isEmpty(), "댓글 신고가 저장되면 안 된다")
        assertTrue(recorded<CommentReportReceivedEvent>().isEmpty(), "댓글 신고 접수 이벤트가 발행되면 안 된다")
    }

    private fun expectError(
        result: ResultActions,
        httpStatus: Int,
        errorCode: Int,
    ) {
        result.andExpect(status().`is`(httpStatus)).andExpect(jsonPath("$.errorCode").value(errorCode))
    }

    private fun postReports(): List<PostReport> =
        tx.execute { em.createQuery("select r from PostReport r order by r.id", PostReport::class.java).resultList }!!

    private fun userReports(): List<UserReport> =
        tx.execute {
            em
                .createQuery(
                    "select r from UserReport r join fetch r.reporter join fetch r.targetUser order by r.id",
                    UserReport::class.java,
                ).resultList
        }!!

    private inline fun <reified T : Any> recorded(): List<T> = applicationEvents.stream(T::class.java).toList()

    private fun assertNoPostReport() {
        assertTrue(postReports().isEmpty(), "게시물 신고가 저장되면 안 된다")
        assertTrue(recorded<PostReportReceivedEvent>().isEmpty(), "게시물 신고 접수 이벤트가 발행되면 안 된다")
    }
}
