package com.aechak.domain.review.review.repository

import com.aechak.domain.review.review.Review

interface ReviewRepository {
    fun findById(id: Long): Review?

    /**
     * 리뷰 행을 잠근 뒤 읽는다. 잠금 읽기는 REPEATABLE READ 스냅샷을 건너뛰고 **최신 커밋**을 보므로,
     * 판정과 반영 사이에 다른 전이가 끼어들지 못한다. 거절된 전이(이미 삭제된 리뷰의 블라인드 등)는
     * 아무것도 쓰지 않아 @Version으로는 걸러지지 않는다 — 잠그지 않으면 운영자 삭제와 해제가 겹쳤을 때
     * 종결된 리뷰가 되살아난다.
     *
     * 리뷰에 매달린 자식 행(셀러 답글)을 쓰는 쪽도 같은 이유로 이걸 거쳐 구매자의 리뷰 삭제와
     * 직렬화한다. 잠금 순서는 항상 reviews -> review_replies다 — 뒤집어 잡으면 삭제 경로와 교착한다.
     */
    fun findByIdForUpdate(id: Long): Review?

    /** 저장하고 즉시 flush한다. */
    fun save(review: Review): Review

    fun existsByOrderItemId(orderItemId: Long): Boolean
}
