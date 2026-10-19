package com.aechak.domain.payment.error

import com.aechak.common.error.ErrorCode

enum class PaymentErrorCode(
    override val code: Int,
    override val message: String,
    override val status: Int,
) : ErrorCode {
    PAYMENT_NOT_FOUND(60000, "결제 내역을 찾을 수 없습니다.", 404),
    PAYMENT_GATEWAY_ERROR(60005, "결제 게이트웨이 호출에 실패했습니다.", 502),
    PAYMENT_ALREADY_PAID(60006, "이미 결제가 완료된 건입니다.", 409),
    INVALID_PAYMENT_AMOUNT(60007, "결제 금액이 올바르지 않습니다.", 400),
    PAYMENT_ORDER_GROUP_NOT_FOUND(60008, "주문 정보를 찾을 수 없습니다.", 404),
    PAYMENT_ORDER_GROUP_NOT_PAYABLE(60009, "결제를 진행할 수 없는 주문입니다. 주문을 다시 진행해 주세요.", 409),
    PAYMENT_AMOUNT_MISMATCH(60010, "결제 금액이 주문 금액과 일치하지 않습니다. 고객센터로 문의해 주세요.", 409),
    PAYMENT_STATE_NOT_SUPPORTED(60011, "지원하지 않는 결제 상태입니다. 고객센터로 문의해 주세요.", 409),
    PAYMENT_ORDER_GROUP_CANCELLED(60012, "만료로 취소된 주문입니다. 결제가 진행되었다면 환불 처리됩니다.", 409),
    PAYMENT_WEBHOOK_INVALID_SIGNATURE(60013, "웹훅 서명 검증에 실패했습니다.", 401),
    PAYMENT_NOT_CANCELLABLE(60014, "취소할 수 없는 결제입니다.", 409),
    CANCEL_AMOUNT_EXCEEDS_CANCELLABLE(60015, "취소 가능 금액을 초과했습니다.", 422),
    PAYMENT_CANCEL_IN_PROGRESS(60016, "이미 취소가 진행 중인 결제입니다.", 409),
    PAYMENT_CANCELLABLE_AMOUNT_CONFLICT(60017, "취소 가능 금액이 변경되었습니다. 다시 시도해 주세요.", 409),
    PAYMENT_CANCEL_FAILED(60018, "결제 취소에 실패했습니다.", 502),
    PAYMENT_CANCEL_AMOUNT_LIMIT_EXCEEDED(60019, "한 번에 취소할 수 있는 금액을 초과했습니다.", 422),

    // 60100대: 결제 대사(reconciliation) 루트
    RECONCILIATION_SOURCE_UNAVAILABLE(60100, "대사할 PG 소스가 설정되지 않았습니다.", 500),
    RECONCILIATION_SOURCE_ERROR(60101, "PG 대사 조회에 실패했습니다.", 502),
    RECONCILIATION_RUN_TAKEN_OVER(60102, "대사 회차를 다른 실행이 이어받아 중단했습니다.", 409),
}
