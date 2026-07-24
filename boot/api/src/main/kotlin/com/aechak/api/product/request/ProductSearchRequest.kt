package com.aechak.api.product.request

import com.aechak.application.product.port.ProductCatalogSort
import com.aechak.application.product.usecase.query.ProductSearchQuery
import com.aechak.common.error.BusinessException
import com.aechak.common.error.CommonErrorCode
import org.hibernate.validator.constraints.Range

/** 상품 목록 조회 요청 파라미터 */
data class ProductSearchRequest(
    val category: Long? = null,
    val sort: String = "latest",
    val cursor: String? = null,
    @field:Range(min = 1, max = 100, message = "size는 1~100 사이여야 합니다.")
    val size: Int = 20,
) {
    fun toQuery() =
        ProductSearchQuery(
            categoryId = category,
            sort = parseSort(sort),
            cursor = cursor,
            size = size,
        )

    private fun parseSort(value: String): ProductCatalogSort =
        when (value) {
            "latest" -> ProductCatalogSort.LATEST
            "price_asc" -> ProductCatalogSort.PRICE_ASC
            else -> throw BusinessException(CommonErrorCode.INVALID_REQUEST)
        }
}
