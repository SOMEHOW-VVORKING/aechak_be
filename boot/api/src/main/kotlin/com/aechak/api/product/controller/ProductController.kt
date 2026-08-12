package com.aechak.api.product.controller

import com.aechak.api.product.request.ProductSearchRequest
import com.aechak.api.product.response.ProductDetailResponse
import com.aechak.api.product.response.ProductListResponse
import com.aechak.api.product.response.ProductOptionsResponse
import com.aechak.application.product.product.usecase.ProductUseCase
import com.aechak.webcommon.response.ApiResponse
import com.aechak.websecurity.authentication.AuthPrincipal
import jakarta.validation.Valid
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.ModelAttribute
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/products")
class ProductController(
    private val productUseCase: ProductUseCase,
) {
    @GetMapping
    fun getProducts(
        @Valid @ModelAttribute request: ProductSearchRequest,
    ): ResponseEntity<ApiResponse<ProductListResponse>> =
        ResponseEntity.ok(
            ApiResponse.of(ProductListResponse.from(productUseCase.getProducts(request.toQuery()))),
        )

    @GetMapping("/{productId}")
    fun getProduct(
        @PathVariable productId: String,
        @AuthenticationPrincipal principal: AuthPrincipal?,
    ): ResponseEntity<ApiResponse<ProductDetailResponse>> =
        ResponseEntity.ok(
            ApiResponse.of(ProductDetailResponse.from(productUseCase.getProduct(productId, principal?.userId))),
        )

    @GetMapping("/{productId}/options")
    fun getProductOptions(
        @PathVariable productId: String,
    ): ResponseEntity<ApiResponse<ProductOptionsResponse>> =
        ResponseEntity.ok(ApiResponse.of(ProductOptionsResponse.from(productUseCase.getProductOptions(productId))))
}
