package com.aechak.domain.review.review

import com.aechak.common.error.BusinessException
import com.aechak.domain.review.error.ReviewErrorCode
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

@Entity
@Table(
    name = "reviews",
    indexes = [
        Index(name = "ix_reviews_product_status_id", columnList = "product_id, review_status, id"),
        Index(name = "ix_reviews_product_status_rating_id", columnList = "product_id, review_status, rating, id"),
    ],
    uniqueConstraints = [UniqueConstraint(name = Review.UK_ORDER_ITEM_ID, columnNames = ["order_item_id"])],
)
class Review protected constructor(
    val productId: Long,
    // 구매 시점 옵션명 스냅샷
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

    fun delete() {
        if (reviewStatus == ReviewStatus.DELETED) {
            throw BusinessException(ReviewErrorCode.INVALID_REVIEW_STATUS_TRANSITION)
        }
        reviewStatus = ReviewStatus.DELETED
        deletedAt = LocalDateTime.now()
    }

    companion object {
        // UNIQUE 제약명
        const val UK_ORDER_ITEM_ID = "uk_reviews_order_item_id"

        /** 포토리뷰 내 이미지 첨부 개수 상한 */
        const val MAX_IMAGES = 5

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
