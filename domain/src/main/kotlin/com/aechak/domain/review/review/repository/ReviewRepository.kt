package com.aechak.domain.review.review.repository

import com.aechak.domain.review.review.Review

interface ReviewRepository {
    fun findById(id: Long): Review?

    /**
     * 상태를 바꿀 때 쓰는 조회. 행을 잠가 읽어야 판정과 반영 사이에 다른 전이가 끼어들지 못한다 —
     * 거절된 전이(이미 삭제된 리뷰의 블라인드 등)는 아무것도 쓰지 않아 @Version으로는 걸러지지 않고,
     * 잠그지 않으면 운영자 삭제와 해제가 겹쳤을 때 종결된 리뷰가 되살아난다.
     */
    fun findByIdForUpdate(id: Long): Review?

    /** 저장하고 즉시 flush한다. */
    fun save(review: Review): Review

    fun existsByOrderItemId(orderItemId: Long): Boolean
}
