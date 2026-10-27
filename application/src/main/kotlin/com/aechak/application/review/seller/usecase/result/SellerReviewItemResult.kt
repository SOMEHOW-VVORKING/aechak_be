package com.aechak.application.review.seller.usecase.result

import com.aechak.application.review.seller.port.view.SellerReviewView
import com.aechak.application.review.usecase.result.ReviewImageItemResult
import com.aechak.application.review.usecase.result.ReviewReplyResult
import com.aechak.domain.review.review.Review
import java.time.LocalDateTime

data class SellerReviewItemResult(
    val reviewId: Long,
    val productId: String,
    val productName: String,
    val rating: Int,
    val content: String,
    val optionName: String,
    val authorNickname: String,
    val createdAt: LocalDateTime,
    val images: List<ReviewImageItemResult>,
    val reply: ReviewReplyResult?,
) {
    companion object {
        fun from(
            view: SellerReviewView,
            images: List<ReviewImageItemResult>,
            authorNickname: String,
        ): SellerReviewItemResult =
            SellerReviewItemResult(
                reviewId = view.id,
                productId = view.productPublicId,
                productName = view.productName,
                rating = view.rating,
                // 마스킹된 리뷰는 셀러도 공개된 본문만 본다 — 구매자 목록과 같은 판정을 쓴다
                content = Review.visibleContent(view.reviewStatus, view.content, view.displayContent),
                optionName = view.optionNameSnapshot,
                authorNickname = authorNickname,
                createdAt = view.createdAt,
                images = images,
                reply =
                    view.replyId?.let {
                        ReviewReplyResult(
                            replyId = it,
                            reviewId = view.id,
                            content = view.replyContent!!,
                            repliedAt = view.replyRepliedAt!!,
                            updatedAt = view.replyUpdatedAt!!,
                        )
                    },
            )
    }
}
