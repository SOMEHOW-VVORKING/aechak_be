package com.aechak.seller.review.request

import com.aechak.application.review.seller.usecase.command.WriteReviewReplyCommand
import com.aechak.domain.review.reply.ReviewReply
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size

/**
 * 길이 판정 기준은 **공백을 포함한 원문**이다(`@Size`가 바인딩된 값을 그대로 본다).
 * 저장은 도메인이 앞뒤 공백을 잘라낸 값으로 하므로 "공백 + 500자"는 잘라내면 500자여도 거절된다 —
 * 공백으로 한도를 늘릴 수 없게 하는 쪽을 골랐다. 두 위반 모두 400 errorCode 90001이고
 * 도메인의 110202는 HTTP 밖 호출자를 위한 마지막 방어선으로 남는다.
 */
data class ReviewReplyWriteRequest(
    @field:NotBlank(message = "답글 내용은 필수입니다.")
    @field:Size(max = ReviewReply.CONTENT_MAX, message = "답글은 최대 {max}자입니다.")
    val content: String,
) {
    fun toCommand(
        sellerId: Long,
        reviewId: Long,
    ): WriteReviewReplyCommand =
        WriteReviewReplyCommand(
            sellerId = sellerId,
            reviewId = reviewId,
            content = content,
        )
}
