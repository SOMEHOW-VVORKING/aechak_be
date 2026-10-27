package com.aechak.application.review.seller.service

import com.aechak.application.review.seller.port.SellerReviewQueryPort
import com.aechak.application.review.seller.usecase.command.WriteReviewReplyCommand
import com.aechak.common.error.BusinessException
import com.aechak.domain.review.error.ReviewErrorCode
import com.aechak.domain.review.reply.ReviewReply
import com.aechak.domain.review.reply.repository.DuplicateReviewReplyException
import com.aechak.domain.review.reply.repository.ReviewReplyRepository
import com.aechak.domain.review.review.repository.ReviewRepository
import org.springframework.stereotype.Service

@Service
class ReviewReplyService(
    private val reviewReplyRepository: ReviewReplyRepository,
    private val reviewRepository: ReviewRepository,
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

    /**
     * 소유권·가시성 판정. 두 걸음으로 나눈 이유가 둘 있다.
     *
     * 1. **없는·남의·비노출 리뷰는 잠금 없이 끊는다.** 없는 id로 바로 `FOR UPDATE`를 걸면
     *    PK 갭 잠금이 생겨 남의 리뷰 작성(INSERT)을 잠깐 막는다.
     * 2. **통과한 리뷰만 잠그고 가시성을 다시 본다.** 조인 조회는 REPEATABLE READ 스냅샷을 보므로
     *    구매자가 방금 커밋한 삭제를 보지 못한다 — 잠금 읽기만 최신 커밋을 본다. 이 재확인이 없으면
     *    "선조회 통과 → 구매자 삭제 커밋 → 답글 INSERT" 순서로 지워진 리뷰에 답글이 매달린다
     *    (목록·구매자 조회 모두 reviews를 조인해 가려 보이지는 않지만, 셀러는 수정·삭제도 못 하는
     *    행에 201을 받는다).
     *
     * 잠금 순서를 reviews -> review_replies로 고정해 구매자 삭제 경로와 같은 방향으로 맞춘다.
     */
    private fun requireOwnedVisibleReview(
        reviewId: Long,
        sellerId: Long,
    ) {
        if (!sellerReviewQueryPort.existsOwnedVisibleReview(reviewId, sellerId)) {
            throw BusinessException(ReviewErrorCode.REVIEW_NOT_FOUND)
        }
        val locked = reviewRepository.findByIdForUpdate(reviewId)
        if (locked == null || !locked.reviewStatus.isVisible()) {
            throw BusinessException(ReviewErrorCode.REVIEW_NOT_FOUND)
        }
    }
}
