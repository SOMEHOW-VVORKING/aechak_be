package com.aechak.domain.review.summary.repository

import com.aechak.domain.review.summary.ReviewAspectExtraction

interface ReviewAspectExtractionRepository {
    fun findAllByReviewIds(reviewIds: Collection<Long>): List<ReviewAspectExtraction>

    fun saveAll(extractions: List<ReviewAspectExtraction>): List<ReviewAspectExtraction>
}
