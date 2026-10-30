package com.aechak.seller.review.response

import com.aechak.application.review.seller.usecase.result.SellerReviewItemResult
import com.aechak.seller.review.response.support.ReviewImageResponse
import java.time.LocalDateTime

data class SellerReviewItemResponse(
    val reviewId: Long,
    val productId: String,
    val productName: String,
    val rating: Int,
    val content: String,
    val optionName: String,
    val authorNickname: String,
    val createdAt: LocalDateTime,
    val images: List<ReviewImageResponse>,
    val reply: SellerReviewReplyResponse?,
) {
    companion object {
        fun from(result: SellerReviewItemResult): SellerReviewItemResponse =
            SellerReviewItemResponse(
                reviewId = result.reviewId,
                productId = result.productId,
                productName = result.productName,
                rating = result.rating,
                content = result.content,
                optionName = result.optionName,
                authorNickname = result.authorNickname,
                createdAt = result.createdAt,
                images = result.images.map(ReviewImageResponse::from),
                reply = result.reply?.let(SellerReviewReplyResponse::from),
            )
    }
}
