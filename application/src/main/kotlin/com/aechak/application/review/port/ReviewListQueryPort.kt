package com.aechak.application.review.port

import com.aechak.application.review.port.view.RatingBucketView
import com.aechak.application.review.port.view.ReviewImageView
import com.aechak.application.review.port.view.ReviewReplyView
import com.aechak.application.review.port.view.ReviewView

interface ReviewListQueryPort {
    fun findVisiblePage(condition: ReviewListCondition): List<ReviewView>

    fun countVisible(
        productId: Long,
        photoOnly: Boolean,
    ): Long

    fun findImagesByReviewIds(reviewIds: Collection<Long>): List<ReviewImageView>

    /** 리뷰에 달린 셀러 답글 — 구매자 목록도 답글을 보여줘야 하므로 페이지 단위로 한 번에 읽는다. */
    fun findRepliesByReviewIds(reviewIds: Collection<Long>): List<ReviewReplyView>

    fun findRatingBuckets(productId: Long): List<RatingBucketView>
}
