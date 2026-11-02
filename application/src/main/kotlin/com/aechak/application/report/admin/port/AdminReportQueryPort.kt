package com.aechak.application.report.admin.port

import com.aechak.application.report.admin.port.view.ReportRowView
import com.aechak.application.report.admin.port.view.ReportTargetCountView
import com.aechak.domain.report.decision.enums.ReportTargetType

interface AdminReportQueryPort {
    /** 한 종류의 신고를 조건에 맞춰 전역 정렬 순서로 limit 건까지 읽는다. */
    fun findPage(
        targetType: ReportTargetType,
        condition: ReportPageCondition,
    ): List<ReportRowView>

    /** 한 종류의 조건에 맞는 전체 건수. 첫 페이지에서만 센다. */
    fun count(
        targetType: ReportTargetType,
        condition: ReportPageCondition,
    ): Long

    /** 없으면 null. 처리된 신고도 돌려준다 — 운영자는 처리 기록을 다시 봐야 한다. */
    fun findOne(
        targetType: ReportTargetType,
        reportId: Long,
    ): ReportRowView?

    /** 대상별 누적 신고 수. 한 페이지의 대상 id 를 묶어 한 번에 묻는다. */
    fun countByTargets(
        targetType: ReportTargetType,
        targetIds: Collection<Long>,
    ): List<ReportTargetCountView>

    /** 같은 대상에 쌓인 신고 이력 — 상세 화면의 "신고 이력". 최근 것부터 limit 건. */
    fun findHistoryByTarget(
        targetType: ReportTargetType,
        targetId: Long,
        limit: Int,
    ): List<ReportRowView>
}
