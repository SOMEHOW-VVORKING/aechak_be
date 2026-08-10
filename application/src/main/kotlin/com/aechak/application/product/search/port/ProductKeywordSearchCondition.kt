package com.aechak.application.product.search.port

import java.time.LocalDateTime

/** 정규화된 키워드와 최신순 커서 조건 */
data class ProductKeywordSearchCondition(
    val keyword: String,
    val lastId: Long?,
    val limit: Int,
    val now: LocalDateTime,
) {
    init {
        require(keyword.isNotBlank()) { "정규화된 검색어는 비어 있을 수 없습니다." }
        require(lastId == null || lastId > 0) { "lastId는 양수여야 합니다." }
        require(limit > 0) { "limit은 양수여야 합니다." }
    }
}
