package com.aechak.domain.product.product.enums

enum class SaleStatus {
    ON_SALE,
    OUT_OF_STOCK,
    SUSPENDED,
    ENDED,
    ;

    /** 셀러가 직접 지정할 수 있는 값. 품절은 재고에서 파생하고 판매종료는 도달 경로가 없음. */
    fun isSellerAssignable(): Boolean = this == ON_SALE || this == SUSPENDED

    /** 구매 가능한 판매 상태인지 여부 판정 */
    fun canPurchase(): Boolean = this == ON_SALE

    companion object {
        /** 목록·검색·찜에 노출 가능한 판매 상태 (구매 가능한 판매중과 상세는 보이는 품절) */
        val EXPOSABLE = setOf(ON_SALE, OUT_OF_STOCK)
    }
}
