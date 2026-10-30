package com.aechak.api.review.response

import com.aechak.application.review.usecase.result.ReviewItemResult
import com.fasterxml.jackson.annotation.JsonProperty
import java.time.LocalDateTime

data class ReviewItemResponse(
    val reviewId: Long,
    val rating: Int,
    val content: String,
    val optionName: String,
    val authorNickname: String,
    val authorProfileImageUrl: String?,
    val createdAt: LocalDateTime,
    val images: List<ReviewImageResponse>,
    @get:JsonProperty("isMine")
    val isMine: Boolean,
    val sellerReply: ReviewReplyResponse?,
) {
    companion object {
        fun from(result: ReviewItemResult): ReviewItemResponse =
            ReviewItemResponse(
                reviewId = result.reviewId,
                rating = result.rating,
                content = result.content,
                optionName = result.optionName,
                authorNickname = result.authorNickname,
                authorProfileImageUrl = result.authorProfileImageUrl,
                createdAt = result.createdAt,
                images = result.images.map(ReviewImageResponse::from),
                isMine = result.isMine,
                sellerReply = result.sellerReply?.let(ReviewReplyResponse::from),
            )
    }
}
