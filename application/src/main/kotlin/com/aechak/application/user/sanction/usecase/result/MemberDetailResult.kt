package com.aechak.application.user.sanction.usecase.result

/** 회원 상세 — 요약 한 벌과 제재 이력. */
data class MemberDetailResult(
    val member: MemberSummaryResult,
    val sanctions: List<SanctionHistoryResult>,
)
