package com.aechak.application.report.admin.usecase

import com.aechak.application.report.admin.usecase.command.DecideReportCommand
import com.aechak.application.report.admin.usecase.query.ReportSearchQuery
import com.aechak.application.report.admin.usecase.result.ReportDetailResult
import com.aechak.application.report.admin.usecase.result.ReportSummaryResult
import com.aechak.application.support.CursorPageResult
import com.aechak.domain.report.decision.enums.ReportTargetType

/**
 * 운영자 신고 조회·처리. 자격(role=ADMIN)은 어드민 모듈 게이트가 보증한다.
 * 비노출·계정 제재는 기존 경로(ProductInspectionGate·AdminReviewUseCase·AdminMemberUseCase)에 위임한다.
 */
interface AdminReportUseCase {
    /** 신고 목록 — 접수 역순. 조건을 바꾼 채 보낸 옛 커서는 400(90002). */
    fun list(query: ReportSearchQuery): CursorPageResult<ReportSummaryResult>

    /** 신고 상세 — 없으면 140000. 처리된 신고도 돌려준다. */
    fun detail(
        targetType: ReportTargetType,
        reportId: Long,
    ): ReportDetailResult

    /**
     * 처리 결정 — 결정 기록 한 행을 남기고 조치를 위임한다.
     * 이미 처리된 신고면 409(140001), 대상 종류에 맞지 않는 처리면 400(140002).
     */
    fun decide(command: DecideReportCommand)
}
