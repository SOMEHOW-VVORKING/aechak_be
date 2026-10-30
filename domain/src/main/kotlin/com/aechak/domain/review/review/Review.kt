package com.aechak.domain.review.review

import com.aechak.common.error.BusinessException
import com.aechak.domain.review.error.ReviewErrorCode
import com.aechak.domain.review.review.enums.ReviewBlindActor
import com.aechak.domain.review.review.enums.ReviewBlindReason
import com.aechak.domain.review.review.enums.ReviewStatus
import com.aechak.domain.support.AggregateRoot
import jakarta.persistence.CascadeType
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Index
import jakarta.persistence.JoinColumn
import jakarta.persistence.OneToMany
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint
import java.time.LocalDateTime
import java.time.LocalTime

@Entity
@Table(
    name = "reviews",
    indexes = [
        Index(name = "ix_reviews_product_status_id", columnList = "product_id, review_status, id"),
        Index(name = "ix_reviews_product_status_rating_id", columnList = "product_id, review_status, rating, id"),
        // 상품을 고르지 않은 운영자 목록의 경로 — 위 두 인덱스는 product_id가 선두라 쓸 수 없다
        Index(name = "ix_reviews_status_id", columnList = "review_status, id"),
    ],
    uniqueConstraints = [UniqueConstraint(name = Review.UK_ORDER_ITEM_ID, columnNames = ["order_item_id"])],
)
class Review protected constructor(
    val productId: Long,
    val optionNameSnapshot: String,
    val orderItemId: Long,
    val authorUserId: Long,
    rating: Int,
    content: String,
) : AggregateRoot() {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0L

    var rating: Int = rating
        protected set

    @Column(columnDefinition = "TEXT", nullable = false)
    var content: String = content
        protected set

    @Column(columnDefinition = "TEXT")
    var displayContent: String? = null
        protected set

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    var reviewStatus: ReviewStatus = ReviewStatus.PUBLIC
        protected set

    @OneToMany(cascade = [CascadeType.ALL], orphanRemoval = true)
    @JoinColumn(name = "review_id", nullable = false, updatable = false)
    private val _images: MutableList<ReviewImage> = mutableListOf()
    val images: List<ReviewImage> get() = _images.toList()

    var deletedAt: LocalDateTime? = null
        protected set

    /** 비노출 처리를 일으킨 주체. 삭제에도 기록한다 — 운영자가 지웠는지 작성자가 지웠는지 가른다. */
    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    var blindActor: ReviewBlindActor? = null
        protected set

    @Enumerated(EnumType.STRING)
    @Column(length = 30)
    var blindReasonCode: ReviewBlindReason? = null
        protected set

    /** 운영자가 남긴 사유 메모. 자동 처리는 비어 있다. */
    @Column(length = BLIND_REASON_TEXT_MAX)
    var blindReasonText: String? = null
        protected set

    var blindedAt: LocalDateTime? = null
        protected set

    var blindedByAdminId: Long? = null
        protected set

    var deletedByAdminId: Long? = null
        protected set

    fun isDeleted(): Boolean = reviewStatus == ReviewStatus.DELETED

    fun isBlinded(): Boolean = reviewStatus == ReviewStatus.BLOCKED

    fun delete() {
        if (isDeleted()) {
            throw BusinessException(ReviewErrorCode.INVALID_REVIEW_STATUS_TRANSITION)
        }
        reviewStatus = ReviewStatus.DELETED
        deletedAt = LocalDateTime.now()
    }

    fun mask(displayContent: String) {
        if (reviewStatus != ReviewStatus.PUBLIC) {
            throw BusinessException(ReviewErrorCode.INVALID_REVIEW_STATUS_TRANSITION)
        }
        reviewStatus = ReviewStatus.MASKED
        this.displayContent = displayContent
    }

    /** 작성 시점 금칙어 판정의 자동 블라인드. */
    fun block() {
        if (reviewStatus != ReviewStatus.PUBLIC) {
            throw BusinessException(ReviewErrorCode.INVALID_REVIEW_STATUS_TRANSITION)
        }
        reviewStatus = ReviewStatus.BLOCKED
        blindActor = ReviewBlindActor.AI
        blindReasonCode = ReviewBlindReason.PROFANITY
        blindedAt = LocalDateTime.now()
    }

    /**
     * 운영자 블라인드. 해제로 되돌릴 수 있다.
     * 마스킹된 리뷰도 블라인드 대상이다 — 대체 본문으로도 가려지지 않는 내용이 블라인드 사유다.
     */
    fun blindByAdmin(
        adminId: Long,
        reasonCode: ReviewBlindReason,
        reasonText: String?,
    ) {
        if (isDeleted()) {
            throw BusinessException(ReviewErrorCode.INVALID_REVIEW_STATUS_TRANSITION)
        }
        if (isBlinded()) {
            throw BusinessException(ReviewErrorCode.REVIEW_ALREADY_BLINDED)
        }
        // 메모 검증을 전이보다 먼저 끝낸다 — 뒤에 두면 거절된 요청이 상태만 바꿔 놓고 나간다
        val normalizedReasonText = normalizeReasonText(reasonText)
        reviewStatus = ReviewStatus.BLOCKED
        blindActor = ReviewBlindActor.ADMIN
        blindReasonCode = reasonCode
        blindReasonText = normalizedReasonText
        blindedAt = LocalDateTime.now()
        blindedByAdminId = adminId
    }

    /**
     * 블라인드 해제. 마스킹 대체 본문이 남아 있으면 MASKED로, 없으면 PUBLIC으로 되돌린다.
     * 블라인드 전 상태를 따로 저장하지 않는 이유는 displayContent가 그 상태의 유일한 원인이라서다 —
     * 대체 본문을 채우는 경로가 mask() 뿐이므로 두 값이 어긋날 자리가 없다.
     */
    fun unblind() {
        if (!isBlinded()) {
            throw BusinessException(ReviewErrorCode.REVIEW_NOT_BLINDED)
        }
        reviewStatus = if (displayContent != null) ReviewStatus.MASKED else ReviewStatus.PUBLIC
        blindActor = null
        blindReasonCode = null
        blindReasonText = null
        blindedAt = null
        blindedByAdminId = null
    }

    /**
     * 운영자 삭제. 해제 경로가 없는 종결이고, 블라인드된 리뷰도 바로 삭제할 수 있다.
     * 행은 남으므로(soft delete) 같은 주문 품목에 리뷰를 다시 쓰는 길은 열리지 않는다.
     */
    fun deleteByAdmin(
        adminId: Long,
        reasonCode: ReviewBlindReason,
        reasonText: String?,
    ) {
        if (isDeleted()) {
            throw BusinessException(ReviewErrorCode.INVALID_REVIEW_STATUS_TRANSITION)
        }
        val normalizedReasonText = normalizeReasonText(reasonText)
        reviewStatus = ReviewStatus.DELETED
        deletedAt = LocalDateTime.now()
        deletedByAdminId = adminId
        blindActor = ReviewBlindActor.ADMIN
        blindReasonCode = reasonCode
        blindReasonText = normalizedReasonText
        blindedAt = null
    }

    companion object {
        const val UK_ORDER_ITEM_ID = "uk_reviews_order_item_id"

        const val MAX_IMAGES = 5

        const val BLIND_REASON_TEXT_MAX = 500

        const val WRITE_WINDOW_DAYS = 30L

        // 구매확정일로부터 30일째 되는 날의 끝
        fun writeDeadline(purchaseConfirmedAt: LocalDateTime): LocalDateTime =
            purchaseConfirmedAt
                .toLocalDate()
                .plusDays(WRITE_WINDOW_DAYS)
                .atTime(LocalTime.MAX)

        fun isWithinWriteWindow(
            purchaseConfirmedAt: LocalDateTime,
            now: LocalDateTime,
        ): Boolean = !writeDeadline(purchaseConfirmedAt).isBefore(now)

        private const val BLINDED_CONTENT = "블라인드 처리된 리뷰입니다."

        private fun normalizeReasonText(reasonText: String?): String? {
            val trimmed = reasonText?.trim()?.takeIf { it.isNotEmpty() } ?: return null
            if (trimmed.length > BLIND_REASON_TEXT_MAX) {
                throw BusinessException(ReviewErrorCode.INVALID_REVIEW_BLIND_REASON)
            }
            return trimmed
        }

        fun visibleContent(
            reviewStatus: ReviewStatus,
            content: String,
            displayContent: String?,
        ): String =
            when (reviewStatus) {
                ReviewStatus.MASKED -> displayContent ?: BLINDED_CONTENT
                else -> content
            }

        fun write(
            productId: Long,
            optionNameSnapshot: String,
            orderItemId: Long,
            authorUserId: Long,
            rating: Int,
            content: String,
            images: List<ReviewImage> = emptyList(),
        ): Review {
            if (rating !in 1..5) {
                throw BusinessException(ReviewErrorCode.INVALID_REVIEW_RATING)
            }
            if (images.size > MAX_IMAGES) {
                throw BusinessException(ReviewErrorCode.REVIEW_TOO_MANY_IMAGES)
            }
            return Review(productId, optionNameSnapshot, orderItemId, authorUserId, rating, content)
                .apply { _images.addAll(images) }
        }
    }
}
