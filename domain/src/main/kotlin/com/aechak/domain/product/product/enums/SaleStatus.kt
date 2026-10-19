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

    /**
     * 주문을 막는 판매 상태 판정 — 막을 값(ENDED·SUSPENDED)만 열거한다.
     * OUT_OF_STOCK을 여기서 안 막는 이유: 이 값은 옵션 재고가 모두 0일 때 이벤트로 뒤늦게
     * 동기화되는 파생 캐시라, 재입고 직후엔 실재고가 있어도 품절로 남아 있을 수 있다.
     * 품절의 진짜 판정은 옵션조합 재고 검사·차감의 몫. (표시용 canPurchase와 갈리는 지점)
     */
    fun canOrder(): Boolean = this != ENDED && this != SUSPENDED

    companion object {
        /** 목록·검색·찜에 노출 가능한 판매 상태 (구매 가능한 판매중과 상세는 보이는 품절) */
        val EXPOSABLE = setOf(ON_SALE, OUT_OF_STOCK)
    }
}
