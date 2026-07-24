package com.aechak.api.product.controller

import com.aechak.api.product.request.ProductSearchRequest
import com.aechak.api.product.response.ProductListResponse
import com.aechak.application.product.usecase.ProductUseCase
import com.aechak.webcommon.response.ApiResponse
import jakarta.validation.Valid
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.ModelAttribute
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
}
