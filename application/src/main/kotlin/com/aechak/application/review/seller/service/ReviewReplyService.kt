package com.aechak.application.review.seller.service

import com.aechak.application.review.seller.port.SellerReviewQueryPort
import com.aechak.application.review.seller.usecase.command.WriteReviewReplyCommand
import com.aechak.common.error.BusinessException
import com.aechak.domain.review.error.ReviewErrorCode
import com.aechak.domain.review.reply.ReviewReply
import com.aechak.domain.review.reply.repository.DuplicateReviewReplyException
import com.aechak.domain.review.reply.repository.ReviewReplyRepository
import org.springframework.stereotype.Service

@Service
class ReviewReplyService(
    private val reviewReplyRepository: ReviewReplyRepository,
    private val sellerReviewQueryPort: SellerReviewQueryPort,
) {
    fun create(command: WriteReviewReplyCommand): ReviewReply {
        requireOwnedVisibleReview(command.reviewId, command.sellerId)
        if (reviewReplyRepository.findByReviewId(command.reviewId) != null) {
            throw BusinessException(ReviewErrorCode.REVIEW_REPLY_ALREADY_EXISTS)
        }
        val reply = ReviewReply.write(command.reviewId, command.sellerId, command.content)
        return try {
            reviewReplyRepository.save(reply)
        } catch (e: DuplicateReviewReplyException) {
            // 선조회와 저장 사이에 다른 요청이 먼저 넣은 경우 — 최종 판정은 UNIQUE 제약이다
            throw BusinessException(ReviewErrorCode.REVIEW_REPLY_ALREADY_EXISTS, e)
        }
    }

    fun edit(command: WriteReviewReplyCommand): ReviewReply {
        val reply = getOwnedReply(command.reviewId, command.sellerId)
        reply.edit(command.content)
        return reviewReplyRepository.save(reply)
    }

    fun delete(
        sellerId: Long,
        reviewId: Long,
    ) {
        reviewReplyRepository.delete(getOwnedReply(reviewId, sellerId))
    }

    /**
     * 답글 수정·삭제의 대상 해석. 답글이 없을 때와 남의 상품 리뷰일 때를 같은 404로 묶지 않고,
     * 소유권 실패는 리뷰 자체를 "없는 것"으로 응답한다 — 셀러에게 남의 리뷰 존재를 알리지 않기 위함이다.
     */
    private fun getOwnedReply(
        reviewId: Long,
        sellerId: Long,
    ): ReviewReply {
        requireOwnedVisibleReview(reviewId, sellerId)
        return reviewReplyRepository.findByReviewId(reviewId)
            ?: throw BusinessException(ReviewErrorCode.REVIEW_REPLY_NOT_FOUND)
    }

    private fun requireOwnedVisibleReview(
        reviewId: Long,
        sellerId: Long,
    ) {
        if (!sellerReviewQueryPort.existsOwnedVisibleReview(reviewId, sellerId)) {
            throw BusinessException(ReviewErrorCode.REVIEW_NOT_FOUND)
        }
    }
}
