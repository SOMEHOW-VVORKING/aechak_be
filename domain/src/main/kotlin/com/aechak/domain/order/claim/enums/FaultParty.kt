package com.aechak.domain.order.claim.enums

enum class FaultParty {
    /** 구매자 귀책 — 반품 발송비 선불(구매자 부담). */
    BUYER,

    /** 판매자 귀책 — 반품 발송비 착불(판매자 부담). */
    SELLER,
}
