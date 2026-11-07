package com.aechak.application.user.sanction.usecase.result

/** 회원 상세 — 요약 한 벌, 현재 프로필 이미지와 소개글, 제재 이력. */
data class MemberDetailResult(
    val member: MemberSummaryResult,
    val profileImageUrl: String?,
    val bio: String?,
    val sanctions: List<SanctionHistoryResult>,
)
