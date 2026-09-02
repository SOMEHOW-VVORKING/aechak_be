package com.aechak.application.review.port

data class ReviewListCondition(
    val productId: Long,
    val sort: ReviewListSort,
    val photoOnly: Boolean,
    val lastReviewId: Long?,
    val lastRating: Int?,
    val limit: Int,
) {
    init {
        require(limit > 0) { "limit은 양수여야 합니다." }
        if (sort == ReviewListSort.RATING_DESC && lastReviewId != null) {
            requireNotNull(lastRating) { "RATING_DESC를 기준으로 정렬할 때는 lastRating이 필수입니다." }
        }
    }
}
