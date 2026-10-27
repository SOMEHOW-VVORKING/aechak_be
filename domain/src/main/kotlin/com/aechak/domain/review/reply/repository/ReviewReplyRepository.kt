package com.aechak.domain.review.reply.repository

import com.aechak.domain.review.reply.ReviewReply

interface ReviewReplyRepository {
    fun findByReviewId(reviewId: Long): ReviewReply?

    /** 저장하고 즉시 flush한다 — UNIQUE 위반을 이 호출 안에서 드러내기 위함. */
    fun save(reviewReply: ReviewReply): ReviewReply

    fun delete(reviewReply: ReviewReply)

    /** 리뷰에 달린 답글을 지우고 삭제된 행 수를 반환한다. 리뷰 삭제 시 고아 답글을 남기지 않기 위해 쓴다. */
    fun deleteByReviewId(reviewId: Long): Int
}
