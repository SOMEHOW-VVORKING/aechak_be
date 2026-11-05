package com.aechak.domain.review.summary.repository

import com.aechak.domain.review.summary.ReviewSummary

interface ReviewSummaryRepository {
    fun findAllByProductId(productId: Long): List<ReviewSummary>

    fun findByProductIdAndScopeKey(
        productId: Long,
        scopeKey: String,
    ): ReviewSummary?

    fun save(summary: ReviewSummary): ReviewSummary
}
