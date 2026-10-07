package com.aechak.api.review.request

import com.aechak.application.review.usecase.command.CreateReviewCommand
import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.Positive
import jakarta.validation.constraints.Size

data class CreateReviewRequest(
    @field:Positive(message = "주문 품목 id가 올바르지 않습니다.")
    @field:Schema(description = "리뷰를 작성할 주문 품목 id")
    val orderItemId: Long,
    val rating: Int,
    @field:Size(
        min = ReviewConstraints.CONTENT_MIN,
        max = ReviewConstraints.CONTENT_MAX,
        message = "리뷰 내용은 {min}자 이상 {max}자 이하여야 합니다.",
    )
    val content: String,
    @field:Schema(description = "presigned 업로드로 받은 tmp 키 목록", example = "[\"tmp/1/reviews/images/xxx.jpg\"]")
    val imageKeys: List<String> = emptyList(),
) {
    fun toCommand(userId: Long): CreateReviewCommand =
        CreateReviewCommand(
            userId = userId,
            orderItemId = orderItemId,
            rating = rating,
            content = content,
            imageKeys = imageKeys,
        )
}
