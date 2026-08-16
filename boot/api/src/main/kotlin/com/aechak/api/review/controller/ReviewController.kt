package com.aechak.api.review.controller

import com.aechak.api.review.request.CreateReviewRequest
import com.aechak.api.review.response.CreateReviewResponse
import com.aechak.application.review.usecase.ReviewCommandUseCase
import com.aechak.webcommon.response.ApiResponse
import com.aechak.websecurity.authentication.AuthPrincipal
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/reviews")
class ReviewController(
    private val reviewCommandUseCase: ReviewCommandUseCase,
) {
    @PostMapping
    fun createReview(
        @Valid @RequestBody request: CreateReviewRequest,
        @AuthenticationPrincipal principal: AuthPrincipal,
    ): ResponseEntity<ApiResponse<CreateReviewResponse>> {
        val result = reviewCommandUseCase.createReview(request.toCommand(principal.userId))
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.of(CreateReviewResponse.from(result)))
    }

    @DeleteMapping("/{reviewId}")
    fun deleteReview(
        @PathVariable reviewId: Long,
        @AuthenticationPrincipal principal: AuthPrincipal,
    ): ResponseEntity<Void> {
        reviewCommandUseCase.deleteReview(principal.userId, reviewId)
        return ResponseEntity.noContent().build()
    }
}
