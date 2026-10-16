package com.aechak.application.payment.reconciliation.usecase.command

import com.aechak.domain.payment.reconciliation.ReconciliationSource
import java.time.LocalDate

/** 기준일 하루치 대사. 창은 기준일 전날 00:00부터 다음 날 00:00까지(KST) — 하루 lookback을 품는다 */
data class ReconcilePaymentsCommand(
    val source: ReconciliationSource,
    val baseDate: LocalDate,
)
