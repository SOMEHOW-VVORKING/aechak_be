package com.aechak.application.report.admin.facade

import com.aechak.application.report.admin.port.AdminReportQueryPort
import com.aechak.application.report.admin.port.ReportPageCondition
import com.aechak.application.report.admin.port.ReportReviewVisibilityPort
import com.aechak.application.report.admin.port.view.ReportRowView
import com.aechak.application.report.admin.port.view.ReportTargetCountView
import com.aechak.application.report.admin.service.AdminReportService
import com.aechak.application.report.admin.usecase.command.DecideReportCommand
import com.aechak.application.review.admin.usecase.AdminReviewUseCase
import com.aechak.application.review.admin.usecase.command.BlindReviewCommand
import com.aechak.application.review.admin.usecase.command.DeleteReviewCommand
import com.aechak.application.review.admin.usecase.command.UnblindReviewCommand
import com.aechak.application.review.admin.usecase.query.AdminReviewSearchQuery
import com.aechak.application.review.admin.usecase.result.AdminReviewDetailResult
import com.aechak.application.review.admin.usecase.result.AdminReviewSummaryResult
import com.aechak.application.support.CursorPageResult
import com.aechak.application.user.sanction.usecase.AdminMemberUseCase
import com.aechak.application.user.sanction.usecase.command.ReleaseSanctionCommand
import com.aechak.application.user.sanction.usecase.command.SanctionMemberCommand
import com.aechak.application.user.sanction.usecase.query.MemberSearchQuery
import com.aechak.application.user.sanction.usecase.result.MemberDetailResult
import com.aechak.application.user.sanction.usecase.result.MemberSummaryResult
import com.aechak.application.user.user.usecase.UserUseCase
import com.aechak.application.user.user.usecase.command.SetNicknameCommand
import com.aechak.application.user.user.usecase.command.UpdateProfileCommand
import com.aechak.application.user.user.usecase.query.UserSearchQuery
import com.aechak.application.user.user.usecase.result.UserAuthorResult
import com.aechak.application.user.user.usecase.result.UserMeResult
import com.aechak.application.user.user.usecase.result.UserPublicProfileResult
import com.aechak.application.user.user.usecase.result.UserSummaryResult
import com.aechak.common.error.BusinessException
import com.aechak.domain.report.decision.ReportDecision
import com.aechak.domain.report.decision.enums.ReportDecisionReason
import com.aechak.domain.report.decision.enums.ReportDecisionType
import com.aechak.domain.report.decision.enums.ReportTargetType
import com.aechak.domain.report.decision.repository.ReportDecisionRepository
import com.aechak.domain.report.error.ReportErrorCode
import com.aechak.domain.user.sanction.enums.SanctionAction
import com.aechak.domain.user.sanction.enums.SanctionReasonCode
import java.time.LocalDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * 계약 테스트. **비노출을 걸지 말지는 잠금 조회의 답으로만 정한다**는 것을 고정한다.
 *
 * 통합 테스트로는 이 축을 확정적으로 태울 수 없다 — 판정과 조치가 한 트랜잭션 안이라
 * 그 사이에 다른 전이를 끼워 넣을 자리가 테스트 쪽에 없다. 그래서 포트를 대역으로 두고
 * "물어봤는지"와 "답을 따랐는지"를 여기서 본다.
 *
 * 깨지면: 조회 시점의 잠기지 않은 스냅숏으로 위임을 건너뛰어, 해제된(노출 중인) 리뷰가
 * 처리 완료(204·SANCTIONED)로 남는다.
 */
class AdminReportFacadeTest {
    private val reports = FakeAdminReportQueryPort()
    private val decisions = FakeReportDecisionRepository()
    private val visibility = ScriptedReviewVisibilityPort()
    private val reviews = RecordingAdminReviewUseCase()
    private val members = RecordingAdminMemberUseCase()
    private val facade =
        AdminReportFacade(
            AdminReportService(reports),
            decisions,
            visibility,
            reviews,
            members,
            StubUserUseCase(),
        )

    @Test
    fun `비노출 판정은 목록 스냅숏이 아니라 잠금 조회에 묻는다`() {
        reports.put(reviewRow())
        visibility.answer = false

        facade.decide(hideCommand())

        assertEquals(
            listOf(REVIEW_ID),
            visibility.lockedReviewIds,
            "조치 직전에 대상 행을 잠그고 현재 노출 여부를 읽어야 한다",
        )
    }

    @Test
    fun `잠금 조회가 노출 중이라고 하면 비노출을 위임한다`() {
        reports.put(reviewRow())
        visibility.answer = false

        facade.decide(hideCommand())

        assertEquals(1, reviews.blinded.size, "노출 중인 리뷰는 실제로 가려야 한다")
        assertEquals(REVIEW_ID, reviews.blinded.single().reviewId())
        assertEquals(1, decisions.saved.size)
    }

    @Test
    fun `잠금 조회가 이미 가려졌다고 하면 위임을 건너뛰되 결정은 남긴다`() {
        reports.put(reviewRow())
        visibility.answer = true

        facade.decide(hideCommand())

        assertTrue(reviews.blinded.isEmpty(), "이미 가려진 리뷰에 다시 걸면 409 로 거절돼 처리가 막힌다")
        assertEquals(1, decisions.saved.size, "조치가 필요 없었을 뿐이므로 결정 기록은 남아야 한다")
    }

    @Test
    fun `잠금 조회에서 대상이 사라졌으면 끊고 결정을 남기지 않는다`() {
        reports.put(reviewRow())
        visibility.answer = null

        val thrown = assertFailsWith<BusinessException> { facade.decide(hideCommand()) }

        assertEquals(
            ReportErrorCode.REPORT_TARGET_NOT_FOUND,
            thrown.errorCode,
            "숨길 대상이 없는데 숨겼다고 기록하면 안 된다",
        )
        assertTrue(reviews.blinded.isEmpty())
    }

    @Test
    fun `계정 제재도 같은 잠금 조회를 거쳐 리뷰를 가린다`() {
        reports.put(reviewRow())
        visibility.answer = false

        facade.decide(
            hideCommand().copy(
                decision = ReportDecisionType.SANCTION_ACCOUNT,
                sanction =
                    DecideReportCommand.SanctionDetail(
                        action = SanctionAction.RESTRICT,
                        reasonCode = SanctionReasonCode.OTHER,
                        reasonDetail = "위반을 확인했습니다",
                        durationDays = null,
                    ),
            ),
        )

        assertEquals(listOf(REVIEW_ID), visibility.lockedReviewIds)
        assertEquals(1, reviews.blinded.size)
        assertEquals(listOf(AUTHOR_ID), members.sanctioned.map { it.targetUserId })
    }

    @Test
    fun `사용자 신고의 계정 제재는 비노출 축이 없어 잠금 조회를 하지 않는다`() {
        reports.put(userRow())
        visibility.answer = null // 물어보면 404 가 되므로, 묻지 않는다는 것이 단언이다

        facade.decide(
            DecideReportCommand(
                adminId = ADMIN_ID,
                targetType = ReportTargetType.USER,
                reportId = REPORT_ID,
                decision = ReportDecisionType.SANCTION_ACCOUNT,
                reasonCode = ReportDecisionReason.ABUSE,
                reasonDetail = "확인했습니다",
                noticeToOwner = null,
                internalMemo = null,
                sanction =
                    DecideReportCommand.SanctionDetail(
                        action = SanctionAction.RESTRICT,
                        reasonCode = SanctionReasonCode.OTHER,
                        reasonDetail = "위반을 확인했습니다",
                        durationDays = null,
                    ),
            ),
        )

        assertTrue(visibility.lockedReviewIds.isEmpty(), "사용자에게는 숨길 콘텐츠가 없다")
        assertTrue(reviews.blinded.isEmpty())
        assertEquals(listOf(TARGET_USER_ID), members.sanctioned.map { it.targetUserId })
    }

    private fun hideCommand() =
        DecideReportCommand(
            adminId = ADMIN_ID,
            targetType = ReportTargetType.REVIEW,
            reportId = REPORT_ID,
            decision = ReportDecisionType.HIDE_TARGET,
            reasonCode = ReportDecisionReason.ABUSE,
            reasonDetail = "확인했습니다",
            noticeToOwner = null,
            internalMemo = null,
            sanction = null,
        )

    private fun reviewRow() =
        ReportRowView(
            targetType = ReportTargetType.REVIEW,
            reportId = REPORT_ID,
            reporterId = 7L,
            reasonCode = "ABUSE",
            reasonText = "욕설이에요",
            reportedAt = LocalDateTime.of(2026, 10, 30, 12, 0),
            targetId = REVIEW_ID,
            targetKey = REVIEW_ID.toString(),
            targetName = "욕설이 섞인 리뷰",
            targetOwnerUserId = AUTHOR_ID,
            decision = null,
            decisionReasonCode = null,
            decidedAt = null,
            decidedByAdminId = null,
        )

    private fun userRow() =
        ReportRowView(
            targetType = ReportTargetType.USER,
            reportId = REPORT_ID,
            reporterId = 7L,
            reasonCode = "ABUSE",
            reasonText = "욕설해요",
            reportedAt = LocalDateTime.of(2026, 10, 30, 12, 0),
            targetId = TARGET_USER_ID,
            targetKey = "01J0USER",
            targetName = null,
            targetOwnerUserId = TARGET_USER_ID,
            decision = null,
            decisionReasonCode = null,
            decidedAt = null,
            decidedByAdminId = null,
        )

    private companion object {
        const val ADMIN_ID = 1L
        const val REPORT_ID = 42L
        const val REVIEW_ID = 500L
        const val AUTHOR_ID = 9L
        const val TARGET_USER_ID = 11L
    }
}

/** 답을 바꿔 끼울 수 있는 잠금 조회 대역. 물어본 리뷰 id 를 순서대로 남긴다. */
private class ScriptedReviewVisibilityPort : ReportReviewVisibilityPort {
    var answer: Boolean? = false
    val lockedReviewIds = mutableListOf<Long>()

    override fun lockAndReadHidden(reviewId: Long): Boolean? {
        lockedReviewIds += reviewId
        return answer
    }
}

private class FakeAdminReportQueryPort : AdminReportQueryPort {
    private val rows = mutableMapOf<Pair<ReportTargetType, Long>, ReportRowView>()

    fun put(row: ReportRowView) {
        rows[row.targetType to row.reportId] = row
    }

    override fun findPage(
        targetType: ReportTargetType,
        condition: ReportPageCondition,
    ): List<ReportRowView> = rows.values.filter { it.targetType == targetType }

    override fun count(
        targetType: ReportTargetType,
        condition: ReportPageCondition,
    ): Long = rows.values.count { it.targetType == targetType }.toLong()

    override fun findOne(
        targetType: ReportTargetType,
        reportId: Long,
    ): ReportRowView? = rows[targetType to reportId]

    override fun countByTargets(
        targetType: ReportTargetType,
        targetIds: Collection<Long>,
    ): List<ReportTargetCountView> = targetIds.map { ReportTargetCountView(targetType, it, 1L) }

    override fun findHistoryByTarget(
        targetType: ReportTargetType,
        targetId: Long,
        limit: Int,
    ): List<ReportRowView> = emptyList()
}

private class FakeReportDecisionRepository : ReportDecisionRepository {
    val saved = mutableListOf<ReportDecision>()

    override fun save(decision: ReportDecision): ReportDecision {
        saved += decision
        return decision
    }

    override fun findByTarget(
        targetType: ReportTargetType,
        reportId: Long,
    ): ReportDecision? = saved.firstOrNull { it.targetType == targetType && it.reportId == reportId }
}

private class RecordingAdminReviewUseCase : AdminReviewUseCase {
    val blinded = mutableListOf<BlindReviewCommand>()

    override fun blind(command: BlindReviewCommand) {
        blinded += command
    }

    override fun list(query: AdminReviewSearchQuery): CursorPageResult<AdminReviewSummaryResult> = unused()

    override fun detail(reviewId: Long): AdminReviewDetailResult = unused()

    override fun unblind(command: UnblindReviewCommand): Unit = unused()

    override fun delete(command: DeleteReviewCommand): Unit = unused()
}

private class RecordingAdminMemberUseCase : AdminMemberUseCase {
    val sanctioned = mutableListOf<SanctionMemberCommand>()

    override fun sanction(command: SanctionMemberCommand) {
        sanctioned += command
    }

    override fun listMembers(query: MemberSearchQuery): CursorPageResult<MemberSummaryResult> = unused()

    override fun getMember(userId: Long): MemberDetailResult = unused()

    override fun release(command: ReleaseSanctionCommand): Unit = unused()
}

private class StubUserUseCase : UserUseCase {
    override fun getAuthors(userIds: Collection<Long>): Map<Long, UserAuthorResult> =
        userIds.associateWith { UserAuthorResult(it, "닉네임$it", withdrawn = false, profileImageUrl = null) }

    override fun checkNickname(
        userId: Long,
        nickname: String,
    ): Boolean = unused()

    override fun setNickname(command: SetNicknameCommand): UserMeResult = unused()

    override fun getMe(userId: Long): UserMeResult = unused()

    override fun updateProfile(command: UpdateProfileCommand): UserMeResult = unused()

    override fun searchUsers(query: UserSearchQuery): List<UserSummaryResult> = unused()

    override fun findPublicProfile(publicId: String): UserPublicProfileResult? = unused()

    override fun findPublicProfileById(userId: Long): UserPublicProfileResult? = unused()

    override fun registerFromSocial(): Long = unused()
}

/** 이 테스트가 쓰지 않는 경로 — 불리면 테스트가 거짓으로 통과하는 것이므로 바로 터뜨린다. */
private fun unused(): Nothing = throw UnsupportedOperationException("이 테스트에서 쓰지 않는 경로다")
