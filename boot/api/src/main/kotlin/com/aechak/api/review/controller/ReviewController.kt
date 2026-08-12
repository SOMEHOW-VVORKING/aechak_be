package com.aechak.api.review.controller

import com.aechak.application.review.usecase.ReviewCommandUseCase
import com.aechak.websecurity.authentication.AuthPrincipal
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/reviews")
class ReviewController(
    private val reviewCommandUseCase: ReviewCommandUseCase,
) {
    @DeleteMapping("/{reviewId}")
    fun deleteReview(
        @PathVariable reviewId: Long,
        @AuthenticationPrincipal principal: AuthPrincipal,
    ): ResponseEntity<Void> {
        reviewCommandUseCase.deleteReview(principal.userId, reviewId)
        return ResponseEntity.noContent().build()
    }
}
