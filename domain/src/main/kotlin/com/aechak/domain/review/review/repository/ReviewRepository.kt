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
}
