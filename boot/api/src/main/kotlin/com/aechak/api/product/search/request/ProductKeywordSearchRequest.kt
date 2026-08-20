package com.aechak.api.product.search.request

import com.aechak.application.product.search.port.ProductKeywordSearchSort
import com.aechak.application.product.search.usecase.query.ProductKeywordSearchQuery
import com.aechak.common.error.BusinessException
import com.aechak.common.error.CommonErrorCode
import com.aechak.webcommon.validation.NotBlankUnicode
import jakarta.validation.constraints.AssertTrue
import jakarta.validation.constraints.DecimalMax
import jakarta.validation.constraints.DecimalMin
import jakarta.validation.constraints.Digits
import jakarta.validation.constraints.PositiveOrZero
import jakarta.validation.constraints.Size
import org.hibernate.validator.constraints.Range
import java.math.BigDecimal

/** 키워드 상품 검색 요청 파라미터 */
data class ProductKeywordSearchRequest(
    @field:NotBlankUnicode(message = "검색어는 필수입니다.")
    @field:Size(max = ProductKeywordSearchQuery.MAX_LENGTH, message = "검색어가 너무 깁니다.")
    val keyword: String = "",
    val sort: String = DEFAULT_SORT,
    @field:PositiveOrZero(message = "minPrice는 0 이상이어야 합니다.")
    val minPrice: Long? = null,
    @field:PositiveOrZero(message = "maxPrice는 0 이상이어야 합니다.")
    val maxPrice: Long? = null,
    @field:DecimalMin(value = "0.0", message = "minRating은 0.0 이상이어야 합니다.")
    @field:DecimalMax(value = "5.0", message = "minRating은 5.0 이하여야 합니다.")
    // 과도한 소수 자릿수로 인한 메모리 고갈 방지
    @field:Digits(integer = 1, fraction = 2, message = "minRating은 정수 1자리, 소수 2자리까지입니다.")
    val minRating: BigDecimal? = null,
    val category: Long? = null,
    val freeShipping: Boolean = false,
    val excludeSoldOut: Boolean = false,
    val cursor: String? = null,
    @field:Range(
        min = ProductKeywordSearchQuery.SIZE_MIN,
        max = ProductKeywordSearchQuery.SIZE_MAX,
        message = "size는 {min}~{max} 사이여야 합니다.",
    )
    val size: Int = ProductKeywordSearchQuery.DEFAULT_SIZE,
) {
    @get:AssertTrue(message = "minPrice는 maxPrice 이하여야 합니다.")
    val isPriceRangeValid: Boolean
        get() = minPrice == null || maxPrice == null || minPrice <= maxPrice

    fun toQuery(): ProductKeywordSearchQuery =
        ProductKeywordSearchQuery(
            keyword = keyword,
            sort = parseSort(sort),
            minPrice = minPrice,
            maxPrice = maxPrice,
            minRating = minRating,
            categoryId = category,
            freeShipping = freeShipping,
            excludeSoldOut = excludeSoldOut,
            cursor = cursor,
            size = size,
        )

    private fun parseSort(value: String): ProductKeywordSearchSort =
        when (value) {
            "popular" -> ProductKeywordSearchSort.POPULAR
            "price_asc" -> ProductKeywordSearchSort.PRICE_ASC
            "latest" -> ProductKeywordSearchSort.LATEST
            else -> throw BusinessException(CommonErrorCode.INVALID_REQUEST)
        }

    companion object {
        const val DEFAULT_SORT = "popular"
    }
}
