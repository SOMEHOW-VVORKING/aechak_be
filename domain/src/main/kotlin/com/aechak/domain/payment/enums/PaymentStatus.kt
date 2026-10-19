package com.aechak.domain.payment.enums

enum class PaymentStatus {
    PENDING,
    APPROVED,
    FAILED,

    /** 취소를 선점했고 PG 응답 전. 원장(취소가능액)은 아직 그대로다 */
    CANCEL_REQUESTED,
    PARTIAL_CANCELLED,
    CANCELLED,
    ;

    /** 승인이 성립한 뒤의 상태 — 취소 진행·완료를 포함한다 */
    val isApprovedOrLater: Boolean
        get() = this == APPROVED || this == CANCEL_REQUESTED || this == PARTIAL_CANCELLED || this == CANCELLED
}
