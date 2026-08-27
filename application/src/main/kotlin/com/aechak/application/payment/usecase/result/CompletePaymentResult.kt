package com.aechak.application.payment.usecase.result

import com.aechak.domain.order.group.OrderGroup
import com.aechak.domain.payment.Payment

/**
 * 결제창이 닫힌 뒤의 진실 확인 결과. 네 상태 모두 정상 사건이라 200으로 나간다 —
 * 계약 위반(금액 불일치·취소된 주문)과 장애(게이트웨이)는 여기 담지 않고 예외로 던진다.
 */
enum class CompletePaymentStatus {
    /** 승인 확인·선점 완료 — 주문그룹·주문이 결제완료로 전이됨 */
    PAID,

    /** PG가 실패로 확정 — 주문그룹은 결제대기 그대로(만료 전까지 재결제 창구) */
    FAILED,

    /** 결제 시도 자체가 없음(결제창 미진입·이탈) — 재시도 안내 */
    NOT_STARTED,

    /** PG 승인 대기 중 — 대기 안내 */
    IN_PROGRESS,
}

data class CompletePaymentResult(
    val status: CompletePaymentStatus,
    val orderGroupId: String,
    val finalPaymentAmount: Long,
    val usedPoint: Long,
    val failureCode: String?,
) {
    companion object {
        fun of(
            status: CompletePaymentStatus,
            group: OrderGroup,
            payment: Payment? = null,
        ): CompletePaymentResult =
            CompletePaymentResult(
                status = status,
                orderGroupId = group.publicId,
                finalPaymentAmount = group.finalPaymentAmount,
                usedPoint = group.usedPoint,
                failureCode = payment?.failureCode,
            )
    }
}
