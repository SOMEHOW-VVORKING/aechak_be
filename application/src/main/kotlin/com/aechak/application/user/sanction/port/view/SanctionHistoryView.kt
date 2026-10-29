package com.aechak.application.user.sanction.port.view

import com.aechak.domain.user.sanction.enums.SanctionAction
import com.aechak.domain.user.sanction.enums.SanctionReasonCode
import java.time.LocalDateTime

/** 제재 이력 한 행. memo 는 운영자 전용이라 어드민 응답에만 실린다. */
data class SanctionHistoryView(
    val sanctionId: Long,
    val action: SanctionAction,
    val reasonCode: SanctionReasonCode?,
    val reasonDetail: String?,
    val effectiveUntil: LocalDateTime?,
    val memo: String?,
    val actedByAdminId: Long,
    val actedAt: LocalDateTime,
)
