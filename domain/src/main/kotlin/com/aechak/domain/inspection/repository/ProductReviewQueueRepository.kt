package com.aechak.domain.inspection.repository

import com.aechak.domain.inspection.ProductReviewQueueItem
import java.time.LocalDateTime

interface ProductReviewQueueRepository {
    fun save(item: ProductReviewQueueItem): ProductReviewQueueItem

    /** 상품의 열린 행. 상품 행을 잠근 트랜잭션 안에서 불러야 열린 행이 하나로 유지됨 */
    fun findPendingByProductId(productId: Long): ProductReviewQueueItem?

    /** 관리자가 반려한 행들. 최근 것부터 */
    fun findRejectedByProductId(productId: Long): List<ProductReviewQueueItem>

    /**
     * 셀러가 지금 열어 둔 이의 건수. 폭주 상한의 분모임.
     *
     * 닫힌 행은 세지 않음 — 셀러가 고친 내용이 통과해 SUPERSEDED로 닫힌 이의는 아무도 결정할
     * 수 없고, 그것을 정원에 세면 "접수 뒤 수정"을 상한만큼 반복한 셀러가 영구히 이의 불가가 됨.
     */
    fun countOpenAppealsByOwner(ownerId: Long): Long

    /** 이 상품의 이의가 마지막으로 기각된 시각. 쿨다운의 기준임. 기각된 적이 없으면 null */
    fun findLastAppealRejectedAt(productId: Long): LocalDateTime?
}
