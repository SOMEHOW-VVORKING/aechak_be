package com.aechak.api.review.response

import com.aechak.application.review.usecase.result.ReviewReplyResult
import java.time.LocalDateTime

/** 구매자에게 보이는 셀러 답글. 답글 식별자는 셀러만 쓰므로 내리지 않는다. */
data class ReviewReplyResponse(
    val content: String,
    val repliedAt: LocalDateTime,
    val updatedAt: LocalDateTime,
) {
    companion object {
        fun from(result: ReviewReplyResult): ReviewReplyResponse =
            ReviewReplyResponse(
                content = result.content,
                repliedAt = result.repliedAt,
                updatedAt = result.updatedAt,
            )
    }
}
