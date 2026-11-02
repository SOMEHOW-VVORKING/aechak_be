package com.aechak.domain.review.summary

import com.aechak.domain.support.BaseEntity
import jakarta.persistence.CascadeType
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.OneToMany
import jakarta.persistence.OrderBy
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint
import java.time.LocalDateTime

/**
 * 상품 하나의 범위별 리뷰 요약. scopeKey가 PRODUCT_SCOPE_KEY면 상품 전체, 그 밖이면 PetWeightBand.segmentKey
 * basisSignature는 이 요약을 만들 때 쓴 노출 리뷰 묶음의 지문. 지금 지문과 다르면 다시 요청함
 */
@Entity
@Table(
    name = "review_summaries",
    uniqueConstraints = [UniqueConstraint(name = "uk_review_summaries_product_scope", columnNames = ["product_id", "scope_key"])],
)
class ReviewSummary protected constructor(
    val productId: Long,
    @Column(nullable = false, length = 30)
    val scopeKey: String,
) : BaseEntity() {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0L

    @Column(nullable = false, length = 60)
    var basisSignature: String = ""
        protected set

    var analyzedReviewCount: Int = 0
        protected set

    /** AI 서버가 분석한 리뷰 수가 요약 기준에 못 미친다고 답했으면 false */
    var enoughReviews: Boolean = false
        protected set

    @Column(length = 40)
    var consAbsenceReason: String? = null
        protected set

    @Column(nullable = false, length = 40)
    var requestId: String = ""
        protected set

    var generatedAt: LocalDateTime? = null
        protected set

    @OneToMany(cascade = [CascadeType.ALL], orphanRemoval = true)
    @JoinColumn(name = "review_summary_id", nullable = false, updatable = false)
    @OrderBy("sortOrder")
    private val _points: MutableList<ReviewSummaryPoint> = mutableListOf()
    val points: List<ReviewSummaryPoint> get() = _points.toList()

    fun isDisplayable(): Boolean = enoughReviews && _points.isNotEmpty()

    fun refresh(
        basisSignature: String,
        analyzedReviewCount: Int,
        enoughReviews: Boolean,
        consAbsenceReason: String?,
        points: List<ReviewSummaryPoint>,
        requestId: String,
        generatedAt: LocalDateTime,
    ) {
        this.basisSignature = basisSignature
        this.analyzedReviewCount = analyzedReviewCount
        this.enoughReviews = enoughReviews
        this.consAbsenceReason = consAbsenceReason
        this.requestId = requestId
        this.generatedAt = generatedAt
        _points.clear()
        _points.addAll(points)
    }

    companion object {
        const val PRODUCT_SCOPE_KEY = "PRODUCT"

        // AI 서버 summary_min_reviews, summary_min_segment_reviews와 같은 값
        const val MIN_PRODUCT_REVIEW_COUNT = 10
        const val MIN_SEGMENT_REVIEW_COUNT = 8

        fun create(
            productId: Long,
            scopeKey: String,
        ): ReviewSummary = ReviewSummary(productId, scopeKey)
    }
}
