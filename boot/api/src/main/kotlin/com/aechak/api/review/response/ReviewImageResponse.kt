package com.aechak.api.review.response

import com.aechak.application.review.usecase.result.ReviewImageItemResult

data class ReviewImageResponse(
    val imageUrl: String,
    val sortOrder: Int,
) {
    companion object {
        fun from(result: ReviewImageItemResult): ReviewImageResponse = ReviewImageResponse(result.imageUrl, result.sortOrder)
    }
}
