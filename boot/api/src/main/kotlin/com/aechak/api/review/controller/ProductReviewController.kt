package com.aechak.api.review.controller

import com.aechak.api.review.request.ReviewListRequest
import com.aechak.api.review.response.ReviewListResponse
import com.aechak.application.review.usecase.ProductReviewUseCase
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
class ProductReviewController(
    private val productReviewUseCase: ProductReviewUseCase,
) {
    @GetMapping("/{productId}/reviews")
    fun getProductReviews(
        @PathVariable productId: String,
        @Valid @ModelAttribute request: ReviewListRequest,
        @AuthenticationPrincipal principal: AuthPrincipal,
    ): ResponseEntity<ApiResponse<ReviewListResponse>> =
        ResponseEntity.ok(
            ApiResponse.of(
                ReviewListResponse.from(productReviewUseCase.getProductReviews(request.toQuery(productId), principal.userId)),
            ),
        )
}
