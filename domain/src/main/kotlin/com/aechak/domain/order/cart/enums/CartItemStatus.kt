package com.aechak.domain.order.cart.enums

/** 저장 컬럼 아님. 조회 시 Catalog 조인으로 파생하며 선언 순서대로 먼저 걸리는 값을 씀. */
enum class CartItemStatus {
    SELLER_UNAVAILABLE,
    SALE_ENDED,
    SALE_SUSPENDED,
    OPTION_UNAVAILABLE,
    OUT_OF_STOCK,
    ACTIVE,
}
