package com.aechak.domain.review.review.repository

import com.aechak.domain.review.review.Review

interface ReviewRepository {
    fun findById(id: Long): Review?

    /** 저장하고 즉시 flush한다. */
    fun save(review: Review): Review

    fun existsByOrderItemId(orderItemId: Long): Boolean

    /** 조건부 원자 소프트 삭제. 실제로 전이된 행 수를 반환한다(이미 삭제면 0 — 무연산·멱등). */
    fun markDeletedIfNotDeleted(
        reviewId: Long,
        userId: Long,
    ): Int

    /** 공개 리뷰만 마스킹으로 전이하고 대체 문구를 채운다. 전이된 행 수를 반환한다(공개가 아니면 0). */
    fun maskIfPublic(
        reviewId: Long,
        displayContent: String,
    ): Int

    /** 공개 리뷰만 차단으로 전이한다. 전이된 행 수를 반환한다(공개가 아니면 0). */
    fun blockIfPublic(reviewId: Long): Int
}
