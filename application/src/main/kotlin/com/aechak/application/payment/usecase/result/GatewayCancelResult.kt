package com.aechak.application.payment.usecase.result

/**
 * PG 취소 호출의 결과 — 원장은 아직 반영되지 않았다.
 * confirmedAmount 가 null 이면 PG가 비동기로 접수만 한 것이고, 그때 원장을 깎으면 대사가 금액 불일치 오보를 낸다.
 */
data class GatewayCancelResult(
    val paymentId: String,
    val confirmedAmount: Long?,
) {
    val isConfirmed: Boolean get() = confirmedAmount != null
}
