package com.aechak.application.payment.port

import java.time.LocalDateTime

/**
 * paymentId 는 Payment.id 가 아님. 포트원에 보내는 결제 단위 ID이므로 현재는 주문 그룹의 PublicId
 */
interface PaymentGatewayPort {
    /** 결제창 호출 전 사전등록 */
    fun preRegister(
        paymentId: String,
        amount: Long,
    )

    fun find(paymentId: String): PaymentGatewayView?

    /** 전액 취소 */
    fun cancel(
        paymentId: String,
        reason: String,
    ): PaymentCancelStatus
}

/** 프로모션 할인 등으로 포트원의 amount.total과 amount.paid이 다를 수 있음 */
data class PaymentGatewayView(
    val status: PaymentGatewayStatus,
    val totalAmount: Long,
    val paidAmount: Long,
    val pgTxId: String?,
    val paidAt: LocalDateTime?,
    /** FAILED일 때 PG 원본 실패 코드(없으면 사유 문자열). 포트원 응답에 없으면 null */
    val failureCode: String? = null,
)

/** 현재는 포트원 결제 상태를 기반으로 함 */
enum class PaymentGatewayStatus {
    READY,
    PAY_PENDING,
    VIRTUAL_ACCOUNT_ISSUED,
    PAID,
    FAILED,
    CANCELLED,
    PARTIAL_CANCELLED,
}

/** 현재는 포트원 내 취소 상태를 기반으로 함 */
enum class PaymentCancelStatus {
    SUCCEEDED,
    REQUESTED,
    FAILED,
}
