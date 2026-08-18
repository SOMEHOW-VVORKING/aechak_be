package com.aechak.domain.user.point.enums

enum class PointTransactionType {
    /** 잠금(주문 예약 등으로 사용 예정 금액 홀드). 주문 결제 흐름은 쓰지 않음 */
    LOCK,

    /** 사용(확정 차감). 주문그룹 생성 시 바로 차감함 */
    USE,

    /** 해제(잠금 취소·환원) */
    RELEASE,

    EARN,
}
