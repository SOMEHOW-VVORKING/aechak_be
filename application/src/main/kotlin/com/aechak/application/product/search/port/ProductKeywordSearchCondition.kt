package com.aechak.application.product.search.port

import java.time.LocalDateTime

/** 정규화된 필터와 정렬, 정렬별 keyset 앵커 조건. now는 유효가격 판정 기준 시각 */
data class ProductKeywordSearchCondition(
    val filter: ProductKeywordFilter,
    val sort: ProductKeywordSearchSort,
    val lastId: Long?,
    val lastReviewCount: Int?,
    val lastPrice: Long?,
    val limit: Int,
    val now: LocalDateTime,
) {
    init {
        require(filter.keyword.isNotBlank()) { "정규화된 검색어는 비어 있을 수 없습니다." }
        require(lastId == null || lastId > 0) { "lastId는 양수여야 합니다." }
        require(limit > 0) { "limit은 양수여야 합니다." }
        if (lastId != null) {
            when (sort) {
                ProductKeywordSearchSort.POPULAR -> {
                    requireNotNull(lastReviewCount) { "POPULAR 정렬 커서에는 lastReviewCount가 필수입니다." }
                }

                ProductKeywordSearchSort.PRICE_ASC -> {
                    requireNotNull(lastPrice) { "PRICE_ASC 정렬 커서에는 lastPrice가 필수입니다." }
                }

                ProductKeywordSearchSort.LATEST -> {
                    Unit
                }
            }
        }
    }
}
