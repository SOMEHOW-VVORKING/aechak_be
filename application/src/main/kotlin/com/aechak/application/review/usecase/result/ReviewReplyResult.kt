package com.aechak.application.review.usecase.result

import com.aechak.application.review.port.view.ReviewReplyView
import com.aechak.domain.review.reply.ReviewReply
import java.time.LocalDateTime

/** 셀러 답글. 구매자 응답에는 식별자를 노출하지 않으므로 어떤 필드를 내릴지는 boot가 정한다. */
data class ReviewReplyResult(
    val replyId: Long,
    val reviewId: Long,
    val content: String,
    val repliedAt: LocalDateTime,
    val updatedAt: LocalDateTime,
) {
    companion object {
        fun from(reply: ReviewReply): ReviewReplyResult =
            ReviewReplyResult(
                replyId = reply.id,
                reviewId = reply.reviewId,
                content = reply.content,
                repliedAt = reply.createdAt,
                updatedAt = reply.updatedAt,
            )

        fun from(view: ReviewReplyView): ReviewReplyResult =
            ReviewReplyResult(
                replyId = view.replyId,
                reviewId = view.reviewId,
                content = view.content,
                repliedAt = view.repliedAt,
                updatedAt = view.updatedAt,
            )
    }
}
