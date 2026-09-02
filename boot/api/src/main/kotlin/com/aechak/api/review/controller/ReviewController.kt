package com.aechak.api.review.controller

import com.aechak.api.review.request.CreateReviewRequest
import com.aechak.api.review.request.MyReviewListRequest
import com.aechak.api.review.request.ReviewListRequest
import com.aechak.api.review.response.CreateReviewResponse
import com.aechak.api.review.response.MyReviewListResponse
import com.aechak.api.review.response.ReviewListResponse
import com.aechak.application.review.usecase.ReviewCommandUseCase
import com.aechak.application.review.usecase.ReviewQueryUseCase
import com.aechak.webcommon.response.ApiResponse
import com.aechak.websecurity.authentication.AuthPrincipal
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.ModelAttribute
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RestController

@RestController
class ReviewController(
    private val reviewQueryUseCase: ReviewQueryUseCase,
    private val reviewCommandUseCase: ReviewCommandUseCase,
) {
    @GetMapping("/products/{productId}/reviews")
    fun getProductReviews(
        @PathVariable("productId") productPublicId: String,
        @Valid @ModelAttribute request: ReviewListRequest,
        @AuthenticationPrincipal principal: AuthPrincipal,
    ): ResponseEntity<ApiResponse<ReviewListResponse>> =
        ResponseEntity.ok(
            ApiResponse.of(
                ReviewListResponse.from(
                    reviewQueryUseCase.getProductReviews(request.toQuery(productPublicId), principal.userId),
                ),
            ),
        )

    @GetMapping("/users/me/reviews")
    fun getMyReviews(
        @Valid @ModelAttribute request: MyReviewListRequest,
        @AuthenticationPrincipal principal: AuthPrincipal,
    ): ResponseEntity<ApiResponse<MyReviewListResponse>> =
        ResponseEntity.ok(
            ApiResponse.of(MyReviewListResponse.from(reviewQueryUseCase.getMyReviews(request.toQuery(principal.userId)))),
        )

    @PostMapping("/reviews")
    fun createReview(
        @Valid @RequestBody request: CreateReviewRequest,
        @AuthenticationPrincipal principal: AuthPrincipal,
    ): ResponseEntity<ApiResponse<CreateReviewResponse>> {
        val result = reviewCommandUseCase.createReview(request.toCommand(principal.userId))
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.of(CreateReviewResponse.from(result)))
    }

    @DeleteMapping("/reviews/{reviewId}")
    fun deleteReview(
        @PathVariable reviewId: Long,
        @AuthenticationPrincipal principal: AuthPrincipal,
    ): ResponseEntity<Void> {
        reviewCommandUseCase.deleteReview(principal.userId, reviewId)
        return ResponseEntity.noContent().build()
    }
}
