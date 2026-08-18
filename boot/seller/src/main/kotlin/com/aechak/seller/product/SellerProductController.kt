package com.aechak.seller.product

import com.aechak.application.product.product.usecase.SellerProductUseCase
import com.aechak.seller.product.request.ProductRegisterRequest
import com.aechak.seller.product.response.ProductRegisterResponse
import com.aechak.webcommon.response.ApiResponse
import com.aechak.websecurity.authentication.AuthPrincipal
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/sellers/me/products")
class SellerProductController(
    private val sellerProductUseCase: SellerProductUseCase,
) {
    @PostMapping
    fun register(
        @Valid @RequestBody request: ProductRegisterRequest,
        @AuthenticationPrincipal principal: AuthPrincipal,
    ): ResponseEntity<ApiResponse<ProductRegisterResponse>> =
        ResponseEntity
            .status(HttpStatus.CREATED)
            .body(ApiResponse.of(ProductRegisterResponse.from(sellerProductUseCase.registerProduct(request.toCommand(principal.userId)))))
}
