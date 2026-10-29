package com.aechak.domain.review.summary

import jakarta.persistence.Column
import jakarta.persistence.Embeddable

@Embeddable
class ReviewSummaryQuote(
    @Column(name = "review_id", nullable = false)
    val reviewId: Long,
    @Column(name = "quote", length = 500, nullable = false)
    val text: String,
)
