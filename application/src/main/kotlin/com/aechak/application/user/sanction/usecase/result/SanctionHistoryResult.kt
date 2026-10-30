package com.aechak.application.user.sanction.usecase.result

import com.aechak.application.user.sanction.port.view.SanctionHistoryView
import com.aechak.domain.user.sanction.enums.SanctionAction
import com.aechak.domain.user.sanction.enums.SanctionReasonCode
import java.time.LocalDateTime

data class SanctionHistoryResult(
    val sanctionId: Long,
    val action: SanctionAction,
    val reasonCode: SanctionReasonCode?,
    val reasonDetail: String?,
    val effectiveUntil: LocalDateTime?,
    val memo: String?,
    val actedByAdminId: Long,
    val actedAt: LocalDateTime,
) {
    companion object {
        fun from(view: SanctionHistoryView): SanctionHistoryResult =
            SanctionHistoryResult(
                sanctionId = view.sanctionId,
                action = view.action,
                reasonCode = view.reasonCode,
                reasonDetail = view.reasonDetail,
                effectiveUntil = view.effectiveUntil,
                memo = view.memo,
                actedByAdminId = view.actedByAdminId,
                actedAt = view.actedAt,
            )
    }
}
