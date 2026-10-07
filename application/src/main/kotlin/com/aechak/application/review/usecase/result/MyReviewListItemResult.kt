package com.aechak.application.review.usecase.result

import com.aechak.application.review.port.view.UnreviewedOrderItemView
import com.aechak.application.review.port.view.WrittenReviewView
import com.aechak.domain.review.review.Review
import com.aechak.domain.review.review.enums.ReviewStatus
import java.time.LocalDate
import java.time.LocalDateTime

sealed interface MyReviewListItemResult

data class WrittenReviewItemResult(
    val reviewId: Long,
    val rating: Int,
    val content: String,
    val reviewStatus: ReviewStatus,
    val optionName: String,
    val productPublicId: String,
    val productName: String,
    val productThumbnailUrl: String?,
    val createdAt: LocalDateTime,
    val images: List<ReviewImageItemResult>,
) : MyReviewListItemResult {
    companion object {
        fun from(
            view: WrittenReviewView,
            productThumbnailUrl: String?,
            images: List<ReviewImageItemResult>,
        ): WrittenReviewItemResult =
            WrittenReviewItemResult(
                reviewId = view.reviewId,
                rating = view.rating,
                content = Review.visibleContent(view.reviewStatus, view.content, view.displayContent),
                reviewStatus = view.reviewStatus,
                optionName = view.optionNameSnapshot,
                productPublicId = view.productPublicId,
                productName = view.productNameSnapshot,
                productThumbnailUrl = productThumbnailUrl,
                createdAt = view.createdAt,
                images = images,
            )
    }
}

data class UnreviewedOrderItemResult(
    val orderItemId: Long,
    val optionName: String,
    val productPublicId: String,
    val productName: String,
    val productThumbnailUrl: String?,
    val purchaseConfirmedAt: LocalDateTime,
    val reviewableUntil: LocalDate,
    val canReview: Boolean,
) : MyReviewListItemResult {
    companion object {
        fun from(
            view: UnreviewedOrderItemView,
            productThumbnailUrl: String?,
            now: LocalDateTime,
        ): UnreviewedOrderItemResult =
            UnreviewedOrderItemResult(
                orderItemId = view.orderItemId,
                optionName = view.optionNameSnapshot,
                productPublicId = view.productPublicId,
                productName = view.productNameSnapshot,
                productThumbnailUrl = productThumbnailUrl,
                purchaseConfirmedAt = view.purchaseConfirmedAt,
                reviewableUntil = Review.writeDeadline(view.purchaseConfirmedAt).toLocalDate(),
                canReview = Review.isWithinWriteWindow(view.purchaseConfirmedAt, now),
            )
    }
}
