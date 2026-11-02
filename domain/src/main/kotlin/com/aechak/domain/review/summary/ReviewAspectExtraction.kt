package com.aechak.domain.review.summary

import com.aechak.domain.review.summary.enums.ReviewExtractionStatus
import com.aechak.domain.support.BaseEntity
import jakarta.persistence.CollectionTable
import jakarta.persistence.Column
import jakarta.persistence.ElementCollection
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Id
import jakarta.persistence.Index
import jakarta.persistence.JoinColumn
import jakarta.persistence.OrderColumn
import jakarta.persistence.Table

/** 리뷰 한 건을 AI가 읽은 결과. 추출에 성공하면 그 뒤로 바뀌지 않음 */
@Entity
@Table(
    name = "review_aspect_extractions",
    indexes = [Index(name = "ix_review_aspect_extractions_product", columnList = "product_id")],
)
class ReviewAspectExtraction protected constructor(
    @Id
    val reviewId: Long,
    val productId: Long,
) : BaseEntity() {
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    var status: ReviewExtractionStatus = ReviewExtractionStatus.UNEXTRACTED
        protected set

    /** 다시 보내도 결과가 같은 사유(주입 시도, 중복, 빈 본문)로 빠졌을 때만 채워짐 */
    @Column(length = 20)
    var excludeReason: String? = null
        protected set

    var failedAttemptCount: Int = 0
        protected set

    @Column(nullable = false, length = 40)
    var extractVersion: String = ""
        protected set

    @ElementCollection
    @CollectionTable(
        name = "review_aspects",
        joinColumns = [JoinColumn(name = "review_id")],
    )
    @OrderColumn(name = "sort_order")
    private val _aspects: MutableList<ReviewAspect> = mutableListOf()
    val aspects: List<ReviewAspect> get() = _aspects.toList()

    /** 다시 요청할 필요가 없는지 */
    fun isSettled(): Boolean =
        status == ReviewExtractionStatus.EXTRACTED || excludeReason != null || failedAttemptCount >= MAX_FAILED_ATTEMPTS

    fun recordExtracted(
        aspects: List<ReviewAspect>,
        extractVersion: String,
    ) {
        if (status == ReviewExtractionStatus.EXTRACTED) return // 재배달이나 늦게 온 결과가 확정된 추출을 덮지 않게 함
        status = ReviewExtractionStatus.EXTRACTED
        excludeReason = null
        this.extractVersion = extractVersion
        _aspects.clear()
        _aspects.addAll(aspects)
    }

    fun recordUnextracted(
        excludeReason: String?,
        extractVersion: String,
    ) {
        if (status == ReviewExtractionStatus.EXTRACTED) return
        this.extractVersion = extractVersion
        if (excludeReason != null) {
            this.excludeReason = excludeReason
        } else {
            failedAttemptCount++
        }
    }

    companion object {
        const val MAX_FAILED_ATTEMPTS = 3

        fun create(
            reviewId: Long,
            productId: Long,
        ): ReviewAspectExtraction = ReviewAspectExtraction(reviewId, productId)
    }
}
