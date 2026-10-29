package com.aechak.infra.persistence.review.summary

import com.aechak.domain.review.summary.ReviewAspectExtraction
import com.aechak.domain.review.summary.repository.ReviewAspectExtractionRepository
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

interface ReviewAspectExtractionJpaRepository : JpaRepository<ReviewAspectExtraction, Long>

@Repository
class ReviewAspectExtractionRepositoryAdapter(
    private val jpaRepository: ReviewAspectExtractionJpaRepository,
) : ReviewAspectExtractionRepository {
    override fun findAllByReviewIds(reviewIds: Collection<Long>): List<ReviewAspectExtraction> =
        if (reviewIds.isEmpty()) emptyList() else jpaRepository.findAllById(reviewIds)

    override fun saveAll(extractions: List<ReviewAspectExtraction>): List<ReviewAspectExtraction> = jpaRepository.saveAll(extractions)
}
