package com.aechak.infra.persistence.inspection

import com.aechak.domain.inspection.ProductReviewQueueItem
import com.aechak.domain.inspection.enums.InspectionTargetType
import com.aechak.domain.inspection.enums.ReviewQueueStatus
import com.aechak.domain.inspection.repository.ProductReviewQueueRepository
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

interface ProductReviewQueueJpaRepository : JpaRepository<ProductReviewQueueItem, Long> {
    fun findFirstByTargetTypeAndTargetIdAndStatus(
        targetType: InspectionTargetType,
        targetId: Long,
        status: ReviewQueueStatus,
    ): ProductReviewQueueItem?

    fun findByTargetTypeAndTargetIdAndStatusOrderByIdDesc(
        targetType: InspectionTargetType,
        targetId: Long,
        status: ReviewQueueStatus,
    ): List<ProductReviewQueueItem>

    fun findFirstByTargetTypeAndTargetIdAndStatusInOrderByIdDesc(
        targetType: InspectionTargetType,
        targetId: Long,
        statuses: Collection<ReviewQueueStatus>,
    ): ProductReviewQueueItem?
}

@Repository
class ProductReviewQueueRepositoryAdapter(
    private val jpaRepository: ProductReviewQueueJpaRepository,
) : ProductReviewQueueRepository {
    override fun save(item: ProductReviewQueueItem): ProductReviewQueueItem = jpaRepository.save(item)

    override fun findPendingByProductId(productId: Long): ProductReviewQueueItem? =
        jpaRepository.findFirstByTargetTypeAndTargetIdAndStatus(
            InspectionTargetType.PRODUCT,
            productId,
            ReviewQueueStatus.PENDING_REVIEW,
        )

    override fun findRejectedByProductId(productId: Long): List<ProductReviewQueueItem> =
        jpaRepository.findByTargetTypeAndTargetIdAndStatusOrderByIdDesc(
            InspectionTargetType.PRODUCT,
            productId,
            ReviewQueueStatus.REJECTED,
        )

    override fun findLatestAdminDecisionByProductId(productId: Long): ProductReviewQueueItem? =
        jpaRepository.findFirstByTargetTypeAndTargetIdAndStatusInOrderByIdDesc(
            InspectionTargetType.PRODUCT,
            productId,
            listOf(ReviewQueueStatus.APPROVED, ReviewQueueStatus.REJECTED),
        )
}
