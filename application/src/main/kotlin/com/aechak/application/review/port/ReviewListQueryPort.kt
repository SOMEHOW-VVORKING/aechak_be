package com.aechak.application.review.port

import com.aechak.application.review.port.view.RatingBucketView
import com.aechak.application.review.port.view.ReviewImageView
import com.aechak.application.review.port.view.ReviewView

interface ReviewListQueryPort {
    fun findVisiblePage(condition: ReviewListCondition): List<ReviewView>

    fun countVisible(
        productId: Long,
        photoOnly: Boolean,
    ): Long

    fun findImagesByReviewIds(reviewIds: Collection<Long>): List<ReviewImageView>

    fun findRatingBuckets(productId: Long): List<RatingBucketView>
}
