package com.aechak.application.report.admin.usecase.query

import com.aechak.application.support.CursorPageSize
import com.aechak.domain.report.decision.enums.ReportHandlingStatus
import com.aechak.domain.report.decision.enums.ReportTargetType
import java.time.LocalDate

/**
 * 신고 목록 검색 조건. 형식이 틀린 필터 값은 필터 없음으로 보정한다(MemberSearchQuery 와 같은 방식).
 * 커서는 보정하지 않고 어긋나면 400 으로 끊는다.
 */
data class ReportSearchQuery(
    val targetType: ReportTargetType?,
    val status: ReportHandlingStatus?,
    val reportedFrom: LocalDate?,
    val reportedTo: LocalDate?,
    val cursor: String?,
    val size: Int,
) {
    companion object {
        fun of(
            targetType: String?,
            status: String?,
            reportedFrom: LocalDate?,
            reportedTo: LocalDate?,
            cursor: String?,
            size: Int,
        ): ReportSearchQuery =
            ReportSearchQuery(
                targetType = targetType?.let { raw -> ReportTargetType.entries.firstOrNull { it.name == raw.normalized() } },
                status = status?.let { raw -> ReportHandlingStatus.entries.firstOrNull { it.name == raw.normalized() } },
                reportedFrom = reportedFrom,
                reportedTo = reportedTo,
                cursor = cursor?.takeIf { it.isNotBlank() },
                size = size.coerceIn(CursorPageSize.MIN.toInt(), CursorPageSize.MAX.toInt()),
            )

        private fun String.normalized(): String = trim().uppercase()
    }
}
