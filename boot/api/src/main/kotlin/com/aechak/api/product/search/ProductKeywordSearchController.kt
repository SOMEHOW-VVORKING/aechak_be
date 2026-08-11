package com.aechak.api.product.search

import com.aechak.api.product.response.ProductListResponse
import com.aechak.api.product.search.request.ProductKeywordSearchRequest
import com.aechak.application.product.search.usecase.ProductKeywordSearchUseCase
import com.aechak.webcommon.response.ApiResponse
import com.aechak.websecurity.authentication.AuthPrincipal
import jakarta.validation.Valid
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.ModelAttribute
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/** 키워드 상품 검색 API */
@RestController
@RequestMapping("/search")
class ProductKeywordSearchController(
    private val productKeywordSearchUseCase: ProductKeywordSearchUseCase,
) {
    @GetMapping("/products")
    fun searchProducts(
        @Valid @ModelAttribute request: ProductKeywordSearchRequest,
        @AuthenticationPrincipal principal: AuthPrincipal?,
    ): ResponseEntity<ApiResponse<ProductListResponse>> =
        ResponseEntity.ok(
            ApiResponse.of(
                ProductListResponse.from(
                    productKeywordSearchUseCase.searchProducts(request.toQuery(), principal?.userId),
                ),
            ),
        )
}
