package com.aechak.domain.report.decision

import com.aechak.common.error.BusinessException
import com.aechak.domain.report.decision.enums.ReportDecisionReason
import com.aechak.domain.report.decision.enums.ReportDecisionType
import com.aechak.domain.report.decision.enums.ReportTargetType
import com.aechak.domain.report.error.ReportErrorCode
import com.aechak.domain.support.BaseEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Index
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint

/**
 * 운영자의 신고 처리 결정 — 추가 전용 기록. 행을 수정하지 않는다.
 * 대상은 (targetType, reportId) 값 참조다 — 한 컬럼이 세 BC 의 서로 다른 표를 가리킨다.
 */
@Entity
@Table(
    name = "report_decisions",
    uniqueConstraints = [
        UniqueConstraint(name = ReportDecision.UK_TARGET, columnNames = ["target_type", "report_id"]),
    ],
    indexes = [Index(name = "ix_report_decisions_admin", columnList = "acted_by_admin_id, id")],
)
class ReportDecision protected constructor(
    targetType: ReportTargetType,
    reportId: Long,
    decision: ReportDecisionType,
    reasonCode: ReportDecisionReason,
    reasonDetail: String?,
    noticeToOwner: String?,
    internalMemo: String?,
    targetOwnerUserId: Long,
    actedByAdminId: Long,
) : BaseEntity() {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0L

    @Enumerated(EnumType.STRING)
    @Column(name = "target_type", length = 20, nullable = false, updatable = false)
    val targetType: ReportTargetType = targetType

    /** 신고 행의 id. 어느 표의 id 인지는 targetType 이 가린다. */
    @Column(name = "report_id", nullable = false, updatable = false)
    val reportId: Long = reportId

    @Enumerated(EnumType.STRING)
    @Column(length = 30, nullable = false)
    val decision: ReportDecisionType = decision

    @Enumerated(EnumType.STRING)
    @Column(length = 30, nullable = false)
    val reasonCode: ReportDecisionReason = reasonCode

    @Column(length = REASON_DETAIL_MAX_LENGTH)
    val reasonDetail: String? = reasonDetail

    /** 대상 소유자에게 전달할 안내 문구. 통보 경로가 없어 보관만 한다. */
    @Column(length = TEXT_MAX_LENGTH)
    val noticeToOwner: String? = noticeToOwner

    /** 운영 내부 메모 — 소유자·신고자에게 내려보내지 않는다. */
    @Column(length = TEXT_MAX_LENGTH)
    val internalMemo: String? = internalMemo

    /** 처리 시점의 대상 소유자(상품=셀러, 리뷰=작성자, 사용자=본인). 결정 시점 값으로 굳힌다. */
    @Column(name = "target_owner_user_id", nullable = false, updatable = false)
    val targetOwnerUserId: Long = targetOwnerUserId

    @Column(name = "acted_by_admin_id", nullable = false, updatable = false)
    val actedByAdminId: Long = actedByAdminId

    companion object {
        const val UK_TARGET = "uk_report_decisions_target_type_report_id"
        const val REASON_DETAIL_MAX_LENGTH = 500
        const val TEXT_MAX_LENGTH = 300

        /** 결정 기록. 대상 종류와 사유가 고른 처리에 맞는지를 여기서 막는다. */
        fun decide(
            targetType: ReportTargetType,
            reportId: Long,
            decision: ReportDecisionType,
            reasonCode: ReportDecisionReason,
            reasonDetail: String?,
            noticeToOwner: String?,
            internalMemo: String?,
            targetOwnerUserId: Long,
            actedByAdminId: Long,
        ): ReportDecision {
            // 사용자 신고에는 숨길 콘텐츠가 없다
            if (decision == ReportDecisionType.HIDE_TARGET && !targetType.hasHidableTarget()) {
                throw BusinessException(ReportErrorCode.REPORT_DECISION_NOT_APPLICABLE)
            }
            // 제재 여부와 사유의 방향이 어긋나면 기록이 자기모순이 된다
            if (decision.sanctions() == reasonCode.indicatesNoViolationByTarget()) {
                throw BusinessException(ReportErrorCode.INVALID_REPORT_DECISION_REASON)
            }
            return ReportDecision(
                targetType = targetType,
                reportId = reportId,
                decision = decision,
                reasonCode = reasonCode,
                reasonDetail = normalize(reasonDetail, REASON_DETAIL_MAX_LENGTH),
                noticeToOwner = normalize(noticeToOwner, TEXT_MAX_LENGTH),
                internalMemo = normalize(internalMemo, TEXT_MAX_LENGTH),
                targetOwnerUserId = targetOwnerUserId,
                actedByAdminId = actedByAdminId,
            )
        }

        private fun normalize(
            text: String?,
            maxLength: Int,
        ): String? {
            val trimmed = text?.trim()
            if (trimmed.isNullOrEmpty()) return null
            // 요청 DTO 가 먼저 막지만 다른 호출부가 생겨도 컬럼 길이를 넘기지 못하게 둔다
            require(trimmed.length <= maxLength) { "본문이 ${maxLength}자를 넘습니다." }
            return trimmed
        }
    }
}
