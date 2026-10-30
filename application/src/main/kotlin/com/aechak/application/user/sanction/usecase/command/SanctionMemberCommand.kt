package com.aechak.application.user.sanction.usecase.command

import com.aechak.domain.user.sanction.enums.SanctionAction
import com.aechak.domain.user.sanction.enums.SanctionReasonCode

/**
 * 제재 적용 입력. 기간은 날짜 수로 받는다 — 화면이 7/14/30일 중 하나를 고르게 하고
 * 만료 시각 계산은 서버가 한다(클라이언트 시계를 믿지 않는다).
 */
data class SanctionMemberCommand(
    val adminId: Long,
    val targetUserId: Long,
    val action: SanctionAction,
    val reasonCode: SanctionReasonCode,
    val reasonDetail: String?,
    val durationDays: Int?,
    val memo: String?,
)
