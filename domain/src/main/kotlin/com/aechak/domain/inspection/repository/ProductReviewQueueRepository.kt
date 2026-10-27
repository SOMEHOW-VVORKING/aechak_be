package com.aechak.domain.inspection.repository

import com.aechak.domain.inspection.ProductReviewQueueItem

interface ProductReviewQueueRepository {
    fun save(item: ProductReviewQueueItem): ProductReviewQueueItem

    /** 상품의 열린 행. 상품 행을 잠근 트랜잭션 안에서 불러야 열린 행이 하나로 유지됨 */
    fun findPendingByProductId(productId: Long): ProductReviewQueueItem?

    /** 관리자가 반려한 행들. 최근 것부터 */
    fun findRejectedByProductId(productId: Long): List<ProductReviewQueueItem>

    /** 관리자가 승인하거나 반려한 행 중 가장 최근 것. 관리자가 결정한 적이 없으면 null */
    fun findLatestAdminDecisionByProductId(productId: Long): ProductReviewQueueItem?
}
