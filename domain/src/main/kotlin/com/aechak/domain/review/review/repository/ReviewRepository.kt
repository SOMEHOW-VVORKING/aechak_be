package com.aechak.domain.review.review.repository

import com.aechak.domain.review.review.Review

interface ReviewRepository {
    fun findById(id: Long): Review?

    /**
     * 리뷰 행을 잠근 뒤 읽는다. 잠금 읽기는 REPEATABLE READ 스냅샷을 건너뛰고 **최신 커밋**을 보므로,
     * 리뷰에 매달린 자식 행(셀러 답글)을 쓰는 쪽이 구매자의 리뷰 삭제와 직렬화할 때 쓴다.
     * 잠금 순서는 항상 reviews -> review_replies다 — 뒤집어 잡으면 삭제 경로와 교착한다.
     */
    fun findByIdForUpdate(id: Long): Review?

    /** 저장하고 즉시 flush한다. */
    fun save(review: Review): Review

    fun existsByOrderItemId(orderItemId: Long): Boolean
}
