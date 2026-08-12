package com.aechak.application.product.product.port

import java.time.LocalDateTime

/**
 * 카탈로그 목록 조회 조건
 *
 * - 노출 조건(APPROVED + ON_SALE/OUT_OF_STOCK + 셀러 ACTIVE + 카테고리 체인 ACTIVE)은 어댑터가 암묵 적용
 * - now: 유효가격(할인 적용가) 판정 기준 시각. 커서 순회 시 첫 페이지 시각으로 고정된다.
 * - lastId/lastPrice: 디코딩이 완료된 keyset 앵커
 */
data class ProductCatalogCondition(
    val categoryId: Long?,
    val sort: ProductCatalogSort,
    val lastId: Long?,
    val lastPrice: Long?,
    val limit: Int,
    val now: LocalDateTime,
) {
    init {
        require(limit > 0) { "limit은 양수여야 합니다." }
        if (sort == ProductCatalogSort.PRICE_ASC && lastId != null) {
            requireNotNull(lastPrice) { "PRICE_ASC을 기준으로 정렬할 때는 lastPrice가 필수입니다." }
        }
    }
}
