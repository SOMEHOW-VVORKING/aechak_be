package com.aechak.domain.user.point.enums

enum class PointTransactionType {
    /** 잠금(주문 예약 등으로 사용 예정 금액 홀드) */
    LOCK,

    /** 사용(확정 차감) */
    USE,

    /** 해제(잠금 취소·환원) */
    RELEASE,

    EARN,
}
