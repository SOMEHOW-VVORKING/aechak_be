package com.aechak.domain.review.reply.repository

import com.aechak.domain.review.reply.ReviewReply

interface ReviewReplyRepository {
    fun findByReviewId(reviewId: Long): ReviewReply?

    /**
     * 답글 행을 잠근 뒤 읽는다. 잠금 읽기는 REPEATABLE READ 스냅샷을 건너뛰고 최신 커밋을 보므로,
     * 수정·삭제가 대상 답글을 해석할 때 쓴다 — 스냅샷으로 읽으면 사이에 지워진 답글을 들고 와
     * 0행 UPDATE/DELETE 로 409(90003)가 나고 계약(404/110200)과 어긋난다.
     * 잠금 순서는 reviews -> review_replies 다.
     */
    fun findByReviewIdForUpdate(reviewId: Long): ReviewReply?

    /** 저장하고 즉시 flush한다 — UNIQUE 위반을 이 호출 안에서 드러내기 위함. */
    fun save(reviewReply: ReviewReply): ReviewReply

    fun delete(reviewReply: ReviewReply)

    /** 리뷰에 달린 답글을 지우고 삭제된 행 수를 반환한다. 리뷰 삭제 시 고아 답글을 남기지 않기 위해 쓴다. */
    fun deleteByReviewId(reviewId: Long): Int
}
