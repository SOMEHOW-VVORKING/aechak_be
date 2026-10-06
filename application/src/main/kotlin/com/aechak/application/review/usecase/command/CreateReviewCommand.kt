package com.aechak.application.review.usecase.command

import com.aechak.application.order.usecase.result.OrderItemForReviewResult
import com.aechak.domain.review.review.Review
import com.aechak.domain.review.review.ReviewImage

/** 리뷰 작성 입력 */
data class CreateReviewCommand(
    val userId: Long,
    val orderItemId: Long,
    val rating: Int,
    val content: String,
    val imageKeys: List<String> = emptyList(),
) {
    fun toEntity(
        orderItem: OrderItemForReviewResult,
        images: List<ReviewImage>,
    ): Review =
        Review.write(
            productId = orderItem.productId,
            optionNameSnapshot = orderItem.optionNameSnapshot,
            orderItemId = orderItemId,
            authorUserId = userId,
            rating = rating,
            content = content,
            images = images,
        )
}
