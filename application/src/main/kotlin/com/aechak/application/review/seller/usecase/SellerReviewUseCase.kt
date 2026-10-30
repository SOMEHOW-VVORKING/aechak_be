package com.aechak.application.review.seller.usecase

import com.aechak.application.review.seller.usecase.command.WriteReviewReplyCommand
import com.aechak.application.review.seller.usecase.query.SellerReviewListQuery
import com.aechak.application.review.seller.usecase.result.SellerReviewListResult
import com.aechak.application.review.usecase.result.ReviewReplyResult

/**
 * 셀러센터가 쓰는 리뷰 진입점. 구매자 경로(ReviewQueryUseCase·ReviewCommandUseCase)와 쓰는 메서드가
 * 겹치지 않아 분리했다 — 한 타입에 두면 양쪽이 안 쓰는 절반을 주입받는다.
 */
interface SellerReviewUseCase {
    fun getMyProductReviews(query: SellerReviewListQuery): SellerReviewListResult

    fun writeReply(command: WriteReviewReplyCommand): ReviewReplyResult

    fun editReply(command: WriteReviewReplyCommand): ReviewReplyResult

    fun deleteReply(
        sellerId: Long,
        reviewId: Long,
    )
}
