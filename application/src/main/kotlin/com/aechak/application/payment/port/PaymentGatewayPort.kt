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

    /** 전액·부분 취소 */
    fun cancel(command: PaymentCancelCommand): PaymentCancelResult
}

/**
 * 취소 요청. amount 가 null 이면 전액 취소다.
 * currentCancellableAmount 는 우리가 알고 있는 취소가능액이고, PG가 이 값과 자기 잔액을 대조해
 * 다르면 거절한다 — 포트원 취소 API에는 멱등키가 없어 중복 취소 방어가 이 대조에 걸려 있다.
 */
data class PaymentCancelCommand(
    val paymentId: String,
    val reason: String,
    val amount: Long?,
    val currentCancellableAmount: Long?,
    val requester: PaymentCancelRequester,
)

/** 취소 요청의 주체. 포트원은 CUSTOMER 면 PG사 정책에 따른 고객 취소로, ADMIN 이면 상점 취소로 처리한다 */
enum class PaymentCancelRequester {
    CUSTOMER,
    ADMIN,
}

/** cancelledAmount 는 PG가 확정한 금액. REQUESTED(비동기 승인)면 아직 확정이 아니라 null 일 수 있다 */
data class PaymentCancelResult(
    val status: PaymentCancelStatus,
    val cancelledAmount: Long?,
    val pgCancellationId: String?,
)

/** 프로모션 할인 등으로 포트원의 amount.total과 amount.paid이 다를 수 있음 */
data class PaymentGatewayView(
    val status: PaymentGatewayStatus,
    val totalAmount: Long,
    val paidAmount: Long,
    /** PG가 취소한 누적 금액. 승인액에서 이만큼 뺀 값이 PG 쪽 취소가능액이다 */
    val cancelledAmount: Long,
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
