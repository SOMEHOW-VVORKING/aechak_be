package com.aechak.domain.review.summary.repository

import com.aechak.domain.review.summary.ReviewSummaryRequest

interface ReviewSummaryRequestRepository {
    fun findByProductId(productId: Long): ReviewSummaryRequest?

    fun save(request: ReviewSummaryRequest): ReviewSummaryRequest
}
