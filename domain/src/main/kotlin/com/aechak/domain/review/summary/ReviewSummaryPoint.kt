package com.aechak.domain.review.summary

import com.aechak.domain.review.summary.enums.AspectPolarity
import com.aechak.domain.support.BaseEntity
import jakarta.persistence.CollectionTable
import jakarta.persistence.Column
import jakarta.persistence.ElementCollection
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.OrderColumn
import jakarta.persistence.Table

/** 요약에 실린 장점이나 단점 한 줄. mentionCount는 AI 서버의 코드가 센 값이고 모델이 쓴 값이 아님 */
@Entity
@Table(name = "review_summary_points")
class ReviewSummaryPoint protected constructor(
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 5)
    val polarity: AspectPolarity,
    @Column(nullable = false, length = 30)
    val aspectCode: String,
    @Column(nullable = false, length = 200)
    val headline: String,
    val mentionCount: Int,
    val sortOrder: Int,
    quotes: List<ReviewSummaryQuote>,
) : BaseEntity() {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0L

    @ElementCollection
    @CollectionTable(name = "review_summary_quotes", joinColumns = [JoinColumn(name = "review_summary_point_id")])
    @OrderColumn(name = "sort_order")
    private val _quotes: MutableList<ReviewSummaryQuote> = quotes.toMutableList()
    val quotes: List<ReviewSummaryQuote> get() = _quotes.toList()

    companion object {
        fun of(
            polarity: AspectPolarity,
            aspectCode: String,
            headline: String,
            mentionCount: Int,
            sortOrder: Int,
            quotes: List<ReviewSummaryQuote>,
        ): ReviewSummaryPoint = ReviewSummaryPoint(polarity, aspectCode, headline, mentionCount, sortOrder, quotes)
    }
}
