package com.aechak.application.payment.port

import com.aechak.domain.payment.reconciliation.PaymentReconciliationItem
import com.aechak.domain.payment.reconciliation.PaymentReconciliationRun

/**
 * 회차가 끝났을 때 사람에게 알리는 창구. 1차 구현은 로그(ERROR)이고 메일·메신저는 어댑터를 바꿔 끼운다.
 * 사람이 봐야 하는 건(MANUAL_REQUIRED·FAILED)만 넘긴다 — 일치 건까지 넘기면 알람이 소음이 된다.
 */
interface PaymentReconciliationAlertPort {
    fun notifyRunFinished(
        run: PaymentReconciliationRun,
        attentionItems: List<PaymentReconciliationItem>,
    )
}
