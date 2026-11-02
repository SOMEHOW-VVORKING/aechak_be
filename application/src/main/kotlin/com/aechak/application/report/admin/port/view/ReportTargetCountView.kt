package com.aechak.application.report.admin.port.view

import com.aechak.domain.report.decision.enums.ReportTargetType

/** 대상 하나에 쌓인 신고 수 — 누적 신고 칼럼의 재료. */
data class ReportTargetCountView(
    val targetType: ReportTargetType,
    val targetId: Long,
    val count: Long,
)
