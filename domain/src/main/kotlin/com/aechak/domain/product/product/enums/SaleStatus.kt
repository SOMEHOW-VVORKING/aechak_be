package com.aechak.domain.product.product.enums

enum class SaleStatus {
    ON_SALE,
    OUT_OF_STOCK,
    SUSPENDED,
    ENDED,
    ;

    /** 셀러가 직접 지정할 수 있는 값. 품절은 재고에서 파생하고 판매종료는 도달 경로가 없음. */
    fun isSellerAssignable(): Boolean = this == ON_SALE || this == SUSPENDED
}
