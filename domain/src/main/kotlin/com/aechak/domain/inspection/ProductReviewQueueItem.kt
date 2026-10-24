package com.aechak.domain.inspection

import com.aechak.domain.inspection.enums.InspectionTargetType
import com.aechak.domain.inspection.enums.ReviewQueueBlockSource
import com.aechak.domain.inspection.enums.ReviewQueueStatus
import com.aechak.domain.support.BaseEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.LocalDateTime

/**
 * AI가 문제를 찾았거나 판단하지 못해 관리자가 확인해야 하는 상품 한 건.
 *
 * 상품마다 열린 행(PENDING_REVIEW)은 하나뿐임. DB 제약이 아니라 상품 행 잠금 아래에서 열린 행을 찾고 쓰는 것으로 지킴.
 * 닫힌 행은 지우지 않고 이력으로 남김.
 */
@Entity
@Table(name = "product_review_queue_items")
class ProductReviewQueueItem protected constructor(
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20, updatable = false)
    val targetType: InspectionTargetType,
    @Column(nullable = false, updatable = false)
    val targetId: Long,
    @Column(nullable = false, updatable = false)
    val ownerId: Long,
    targetName: String?,
    blindedAt: LocalDateTime,
) : BaseEntity() {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long = 0
        protected set

    @Column(length = TARGET_NAME_MAX)
    var targetName: String? = targetName
        protected set

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30, updatable = false)
    val blockSource: ReviewQueueBlockSource = ReviewQueueBlockSource.AI_DETECTION

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    var status: ReviewQueueStatus = ReviewQueueStatus.PENDING_REVIEW
        protected set

    /** 이 행을 열었거나 다시 연 판정의 시각 */
    @Column(nullable = false)
    var blindedAt: LocalDateTime = blindedAt
        protected set

    @Column(length = REJECT_REASON_MAX)
    var rejectReason: String? = null
        protected set

    fun isPending(): Boolean = status == ReviewQueueStatus.PENDING_REVIEW

    /** 열린 행에 새 판정이 또 왔을 때. 행을 새로 만들지 않고 시각과 상품명만 갱신함 */
    fun refresh(
        targetName: String?,
        blindedAt: LocalDateTime,
    ) {
        requirePending()
        this.targetName = targetName
        this.blindedAt = blindedAt
    }

    fun approve() {
        requirePending()
        status = ReviewQueueStatus.APPROVED
    }

    fun reject(reason: String) {
        requirePending()
        require(reason.isNotBlank()) { "반려 사유가 비어 있음" }
        status = ReviewQueueStatus.REJECTED
        rejectReason = reason
    }

    fun supersede() {
        requirePending()
        status = ReviewQueueStatus.SUPERSEDED
    }

    private fun requirePending() {
        check(isPending()) { "이미 닫힌 검토 행임. id=$id status=$status" }
    }

    companion object {
        const val TARGET_NAME_MAX = 255
        const val REJECT_REASON_MAX = 500

        fun openForProduct(
            productId: Long,
            sellerId: Long,
            productName: String?,
            blindedAt: LocalDateTime,
        ): ProductReviewQueueItem =
            ProductReviewQueueItem(
                targetType = InspectionTargetType.PRODUCT,
                targetId = productId,
                ownerId = sellerId,
                targetName = productName,
                blindedAt = blindedAt,
            )
    }
}
