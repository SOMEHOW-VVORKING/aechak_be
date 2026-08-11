package com.aechak.api.product.controller

import com.aechak.api.product.request.LikedProductListRequest
import com.aechak.api.product.response.ProductListResponse
import com.aechak.application.product.like.usecase.ProductLikeUseCase
import com.aechak.webcommon.response.ApiResponse
import com.aechak.websecurity.authentication.AuthPrincipal
import jakarta.validation.Valid
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.ModelAttribute
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/users/me/liked-products")
class LikedProductController(
    private val productLikeUseCase: ProductLikeUseCase,
) {
    @GetMapping
    fun getLikedProducts(
        @Valid @ModelAttribute request: LikedProductListRequest,
        @AuthenticationPrincipal principal: AuthPrincipal,
    ): ResponseEntity<ApiResponse<ProductListResponse>> =
        ResponseEntity.ok(
            ApiResponse.of(
                ProductListResponse.from(productLikeUseCase.getLikedProducts(request.toQuery(), principal.userId)),
            ),
        )
}
