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
import jakarta.persistence.JoinColumn
import jakarta.persistence.OneToMany
import jakarta.persistence.Table
import java.time.LocalDateTime

@Entity
@Table(name = "reviews")
class Review protected constructor(
    val productId: Long,
    val optionCombinationId: Long,
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
        fun write(
            productId: Long,
            optionCombinationId: Long,
            orderItemId: Long,
            authorUserId: Long,
            rating: Int,
            content: String,
        ): Review {
            if (rating !in 1..5) {
                throw BusinessException(ReviewErrorCode.INVALID_REVIEW_RATING)
            }
            return Review(productId, optionCombinationId, orderItemId, authorUserId, rating, content)
        }
    }
}
