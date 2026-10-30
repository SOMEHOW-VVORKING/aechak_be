package com.aechak.application.report.admin.port

import com.aechak.domain.report.decision.enums.ReportTargetType
import java.time.LocalDateTime

/**
 * 신고 목록 조회 조건. 어댑터가 대상 종류마다 한 번씩 적용한다.
 * 전역 정렬은 `reportedAt DESC, targetType ASC, reportId DESC` 이고 앵커 세 축이 그 순서의 한 점을 가리킨다.
 */
data class ReportPageCondition(
    /** 읽을 종류. 필터가 없으면 세 종류 전부가 들어온다 — 비어 있는 채로 오지 않는다 */
    val targetTypes: Set<ReportTargetType>,
    val statuses: ReportStatusCondition,
    val reportedFrom: LocalDateTime?,
    /** 반열림 구간의 상한 — 다음 날 0시가 들어온다 */
    val reportedToExclusive: LocalDateTime?,
    val lastReportedAt: LocalDateTime?,
    val lastTargetType: ReportTargetType?,
    val lastReportId: Long?,
    val limit: Int,
) {
    init {
        require(targetTypes.isNotEmpty()) { "읽을 대상 종류가 없습니다." }
        // 앵커는 세 축이 함께 와야 한 점을 가리킨다
        val anchors = listOfNotNull(lastReportedAt, lastTargetType, lastReportId)
        require(anchors.isEmpty() || anchors.size == 3) { "커서 앵커는 세 축이 함께 있어야 합니다." }
    }

    val anchored: Boolean get() = lastReportedAt != null
}

/** 처리 상태 필터. 상태가 report_decisions 행에서 파생하므로 조건의 모양이 둘로 갈린다. */
enum class ReportStatusCondition {
    /** 필터 없음 */
    ANY,

    /** 결정 기록이 없는 건만 */
    UNDECIDED,

    /** 제재 없이 종결한 건만 */
    DISMISSED,

    /** 비공개 전환·계정 제재로 종결한 건만 */
    SANCTIONED,
}
