package com.aechak.application.report.admin.service

import com.aechak.application.report.admin.port.AdminReportQueryPort
import com.aechak.application.report.admin.port.ReportPageCondition
import com.aechak.application.report.admin.port.ReportStatusCondition
import com.aechak.application.report.admin.port.view.ReportRowView
import com.aechak.application.report.admin.support.ReportCursorCodec
import com.aechak.application.report.admin.usecase.query.ReportSearchQuery
import com.aechak.application.support.CursorPageSize
import com.aechak.common.error.BusinessException
import com.aechak.common.error.CommonErrorCode
import com.aechak.domain.report.decision.enums.ReportHandlingStatus
import com.aechak.domain.report.decision.enums.ReportTargetType
import com.aechak.domain.report.error.ReportErrorCode
import org.springframework.stereotype.Service
import java.time.LocalDate

/** 신고 조회 로직 보관함 — Facade 에서만 호출된다. 세 신고 표를 한 목록으로 병합한다. */
@Service
class AdminReportService(
    private val adminReportQueryPort: AdminReportQueryPort,
) {
    /**
     * 조건에 맞는 신고를 전역 정렬 순서로 limit 건까지 모은다.
     * 표마다 limit 건씩 읽어 병합한다 — 전역 상위 N 건에 드는 행은 자기 표에서도 상위 N 건 안에 있다.
     */
    fun findPage(query: ReportSearchQuery): List<ReportRowView> {
        val condition = toCondition(query)
        return condition.targetTypes
            .flatMap { adminReportQueryPort.findPage(it, condition) }
            .sortedWith(GLOBAL_ORDER)
            .take(condition.limit)
    }

    /** 조건에 맞는 전체 건수. 호출부가 첫 페이지에서만 부른다. */
    fun countAll(query: ReportSearchQuery): Long {
        val condition = toCondition(query)
        return condition.targetTypes.sumOf { adminReportQueryPort.count(it, condition) }
    }

    fun findOne(
        targetType: ReportTargetType,
        reportId: Long,
    ): ReportRowView = adminReportQueryPort.findOne(targetType, reportId) ?: throw BusinessException(ReportErrorCode.REPORT_NOT_FOUND)

    /** 같은 대상의 신고 이력. 보고 있는 건을 빼는 것은 호출부 몫이다. */
    fun findHistory(
        targetType: ReportTargetType,
        targetId: Long,
    ): List<ReportRowView> = adminReportQueryPort.findHistoryByTarget(targetType, targetId, HISTORY_LIMIT)

    /** 대상별 누적 신고 수. 한 페이지 분량을 종류마다 묶어 묻는다. */
    fun countByTargets(rows: List<ReportRowView>): Map<TargetRef, Long> =
        rows
            .groupBy({ it.targetType }, { it.targetId })
            .flatMap { (targetType, targetIds) ->
                adminReportQueryPort.countByTargets(targetType, targetIds.distinct())
            }.associate { TargetRef(it.targetType, it.targetId) to it.count }

    data class TargetRef(
        val targetType: ReportTargetType,
        val targetId: Long,
    )

    private fun toCondition(query: ReportSearchQuery): ReportPageCondition {
        val decoded = query.cursor?.let(ReportCursorCodec::decode)
        // 조건을 바꾼 채 옛 커서를 보내면 두 집합이 섞이거나 사이 구간이 빠진다
        if (decoded != null && decoded.filterDigest != ReportCursorCodec.digestOf(query)) {
            throw BusinessException(CommonErrorCode.INVALID_CURSOR)
        }
        return ReportPageCondition(
            targetTypes = query.targetType?.let { setOf(it) } ?: ReportTargetType.entries.toSet(),
            statuses = toStatusCondition(query.status),
            reportedFrom = query.reportedFrom?.atStartOfDay(),
            // 반열림 구간 — 입력한 날짜의 하루치가 빠지지 않게 다음 날 0시를 상한으로 쓴다.
            // LocalDate.MAX 는 다음 날이 없어 조건에서 뺀다
            reportedToExclusive =
                query.reportedTo
                    ?.takeIf { it < LocalDate.MAX }
                    ?.plusDays(1)
                    ?.atStartOfDay(),
            lastReportedAt = decoded?.lastReportedAt,
            lastTargetType = decoded?.lastTargetType,
            lastReportId = decoded?.lastReportId,
            limit = CursorPageSize.fetchLimit(query.size),
        )
    }

    private fun toStatusCondition(status: ReportHandlingStatus?): ReportStatusCondition =
        when (status) {
            null -> ReportStatusCondition.ANY
            ReportHandlingStatus.RECEIVED -> ReportStatusCondition.UNDECIDED
            ReportHandlingStatus.DISMISSED -> ReportStatusCondition.DISMISSED
            ReportHandlingStatus.SANCTIONED -> ReportStatusCondition.SANCTIONED
        }

    companion object {
        /** 상세가 보여 주는 이력 상한. 회원 상세와 같은 값이다. */
        const val HISTORY_LIMIT = 50

        /**
         * 전역 정렬: 접수 역순, 같은 시각이면 종류 이름순, 그 안에서 신고 id 역순.
         * 어댑터의 keyset 조건이 이 순서와 같아야 한다.
         */
        private val GLOBAL_ORDER: Comparator<ReportRowView> =
            compareByDescending<ReportRowView> { it.reportedAt }
                .thenBy { it.targetType.name }
                .thenByDescending { it.reportId }
    }
}
