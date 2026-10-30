package com.aechak.application.report.admin.port.view

import com.aechak.domain.report.decision.enums.ReportDecisionReason
import com.aechak.domain.report.decision.enums.ReportDecisionType
import com.aechak.domain.report.decision.enums.ReportTargetType
import java.time.LocalDateTime

/** 신고 한 건의 조회 결과 — 세 신고 표를 공통 모양으로 눕힌다. */
data class ReportRowView(
    val targetType: ReportTargetType,
    val reportId: Long,
    val reporterId: Long,
    /** 종류별 사유 enum 의 이름. 세 표의 enum 타입이 서로 달라 문자열로 눕힌다 */
    val reasonCode: String,
    val reasonText: String?,
    val reportedAt: LocalDateTime,
    /** 대상의 내부 id — 누적 신고를 세는 축 */
    val targetId: Long,
    /** 화면이 대상을 다시 열 때 쓰는 식별자. 상품은 publicId, 나머지는 내부 id 의 문자열 */
    val targetKey: String,
    val targetName: String?,
    /** 대상 소유자 — 상품은 셀러, 리뷰는 작성자, 사용자는 본인 */
    val targetOwnerUserId: Long,
    /** 대상이 이미 구매자에게 안 보이는지 (상품: 검수 미승인, 리뷰: 비노출·삭제). 사용자는 항상 false */
    val targetAlreadyHidden: Boolean,
    /** 내려진 결정. 없으면 아직 미처리(RECEIVED) */
    val decision: ReportDecisionType?,
    val decisionReasonCode: ReportDecisionReason?,
    val decidedAt: LocalDateTime?,
    val decidedByAdminId: Long?,
)
