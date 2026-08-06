package com.aechak.api.product.search.request

import com.aechak.application.product.search.usecase.query.ProductKeywordSearchQuery
import com.aechak.webcommon.validation.NotBlankUnicode
import jakarta.validation.constraints.Size
import org.hibernate.validator.constraints.Range

/** 키워드 상품 검색 요청 파라미터 */
data class ProductKeywordSearchRequest(
    @field:NotBlankUnicode(message = "검색어는 필수입니다.")
    @field:Size(max = ProductKeywordSearchQuery.MAX_LENGTH, message = "검색어가 너무 깁니다.")
    val keyword: String = "",
    val cursor: String? = null,
    @field:Range(
        min = ProductKeywordSearchQuery.SIZE_MIN,
        max = ProductKeywordSearchQuery.SIZE_MAX,
        message = "size는 {min}~{max} 사이여야 합니다.",
    )
    val size: Int = ProductKeywordSearchQuery.DEFAULT_SIZE,
) {
    fun toQuery(): ProductKeywordSearchQuery = ProductKeywordSearchQuery(keyword = keyword, cursor = cursor, size = size)
}
