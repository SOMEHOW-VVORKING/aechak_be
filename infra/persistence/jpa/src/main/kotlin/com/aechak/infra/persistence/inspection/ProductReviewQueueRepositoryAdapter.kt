package com.aechak.infra.persistence.inspection

import com.aechak.domain.inspection.ProductReviewQueueItem
import com.aechak.domain.inspection.enums.InspectionTargetType
import com.aechak.domain.inspection.enums.ReviewQueueStatus
import com.aechak.domain.inspection.repository.ProductReviewQueueRepository
import jakarta.persistence.LockModeType
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository
import java.time.LocalDateTime

interface ProductReviewQueueJpaRepository : JpaRepository<ProductReviewQueueItem, Long> {
    fun findFirstByTargetTypeAndTargetIdAndStatus(
        targetType: InspectionTargetType,
        targetId: Long,
        status: ReviewQueueStatus,
    ): ProductReviewQueueItem?

    /**
     * 열린 행의 잠금 조회. 하나만 나오는 것이 불변식이지만 목록으로 받는다 —
     * 단건으로 받으면 이미 갈라진 데이터에서 예외가 나 복구 경로까지 함께 막힌다.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query(
        "select i from ProductReviewQueueItem i " +
            "where i.targetType = :targetType and i.targetId = :targetId and i.status = :status order by i.id",
    )
    fun findOpenForUpdate(
        @Param("targetType") targetType: InspectionTargetType,
        @Param("targetId") targetId: Long,
        @Param("status") status: ReviewQueueStatus,
    ): List<ProductReviewQueueItem>

    fun findByTargetTypeAndTargetIdAndStatusOrderByIdDesc(
        targetType: InspectionTargetType,
        targetId: Long,
        status: ReviewQueueStatus,
    ): List<ProductReviewQueueItem>

    fun countByTargetTypeAndOwnerIdAndStatusAndAppealedAtIsNotNull(
        targetType: InspectionTargetType,
        ownerId: Long,
        status: ReviewQueueStatus,
    ): Long

    /** resolvedAt 내림차순. MySQL은 내림차순에서 NULL을 뒤로 보내므로 닫힌 시각이 적힌 행이 먼저 옴 */
    fun findFirstByTargetTypeAndTargetIdAndStatusAndAppealedAtIsNotNullOrderByResolvedAtDesc(
        targetType: InspectionTargetType,
        targetId: Long,
        status: ReviewQueueStatus,
    ): ProductReviewQueueItem?

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

    override fun findPendingByProductIdForUpdate(productId: Long): ProductReviewQueueItem? =
        jpaRepository
            .findOpenForUpdate(
                InspectionTargetType.PRODUCT,
                productId,
                ReviewQueueStatus.PENDING_REVIEW,
            ).firstOrNull()

    override fun findRejectedByProductId(productId: Long): List<ProductReviewQueueItem> =
        jpaRepository.findByTargetTypeAndTargetIdAndStatusOrderByIdDesc(
            InspectionTargetType.PRODUCT,
            productId,
            ReviewQueueStatus.REJECTED,
        )

    // targetType을 함께 거는 이유는 리뷰 검수 큐가 생겼을 때 상품 상한의 분모가 오염되지 않게 하는 것
    override fun countOpenAppealsByOwner(ownerId: Long): Long =
        jpaRepository.countByTargetTypeAndOwnerIdAndStatusAndAppealedAtIsNotNull(
            InspectionTargetType.PRODUCT,
            ownerId,
            ReviewQueueStatus.PENDING_REVIEW,
        )

    override fun findLastAppealRejectedAt(productId: Long): LocalDateTime? =
        jpaRepository
            .findFirstByTargetTypeAndTargetIdAndStatusAndAppealedAtIsNotNullOrderByResolvedAtDesc(
                InspectionTargetType.PRODUCT,
                productId,
                ReviewQueueStatus.REJECTED,
            )?.resolvedAt

    override fun findLatestAdminDecisionByProductId(productId: Long): ProductReviewQueueItem? =
        jpaRepository.findFirstByTargetTypeAndTargetIdAndStatusInOrderByIdDesc(
            InspectionTargetType.PRODUCT,
            productId,
            listOf(ReviewQueueStatus.APPROVED, ReviewQueueStatus.REJECTED),
        )
}
