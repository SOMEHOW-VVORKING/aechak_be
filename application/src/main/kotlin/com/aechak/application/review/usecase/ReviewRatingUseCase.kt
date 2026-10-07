package com.aechak.application.review.usecase

interface ReviewRatingUseCase {
    /** 상품의 리뷰 평점을 다시 세어 상품 통계에 반영한다. */
    fun recomputeProductRating(productId: Long)
}
