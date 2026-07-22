package com.aechak.api.product.controller

import com.aechak.api.product.response.ProductListResponse
import com.aechak.application.product.port.ProductCatalogSort
import com.aechak.application.product.usecase.ProductUseCase
import com.aechak.application.product.usecase.query.ProductSearchQuery
import com.aechak.common.error.BusinessException
import com.aechak.common.error.CommonErrorCode
import com.aechak.webcommon.response.ApiResponse
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/products")
class ProductController(
    private val productUseCase: ProductUseCase,
) {
    @GetMapping
    fun getProducts(
        @RequestParam(required = false) category: Long?,
        @RequestParam(defaultValue = "latest") sort: String,
        @RequestParam(required = false) cursor: String?,
        @RequestParam(defaultValue = "20") size: Int,
    ): ResponseEntity<ApiResponse<ProductListResponse>> {
        if (size !in ProductSearchQuery.SIZE_RANGE) {
            throw BusinessException(CommonErrorCode.INVALID_REQUEST)
        }
        val query =
            ProductSearchQuery(
                categoryId = category,
                sort = parseSort(sort),
                cursor = cursor,
                size = size,
            )
        return ResponseEntity.ok(ApiResponse.of(ProductListResponse.from(productUseCase.getProducts(query))))
    }

    private fun parseSort(sort: String): ProductCatalogSort =
        when (sort) {
            "latest" -> ProductCatalogSort.LATEST
            "price_asc" -> ProductCatalogSort.PRICE_ASC
            else -> throw BusinessException(CommonErrorCode.INVALID_REQUEST)
        }
}
