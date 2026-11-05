package com.aechak.infra.persistence.review.summary

import com.aechak.domain.review.summary.ReviewSummary
import com.aechak.domain.review.summary.repository.ReviewSummaryRepository
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

interface ReviewSummaryJpaRepository : JpaRepository<ReviewSummary, Long> {
    fun findAllByProductId(productId: Long): List<ReviewSummary>

    fun findByProductIdAndScopeKey(
        productId: Long,
        scopeKey: String,
    ): ReviewSummary?
}

@Repository
class ReviewSummaryRepositoryAdapter(
    private val jpaRepository: ReviewSummaryJpaRepository,
) : ReviewSummaryRepository {
    override fun findAllByProductId(productId: Long): List<ReviewSummary> = jpaRepository.findAllByProductId(productId)

    override fun findByProductIdAndScopeKey(
        productId: Long,
        scopeKey: String,
    ): ReviewSummary? = jpaRepository.findByProductIdAndScopeKey(productId, scopeKey)

    override fun save(summary: ReviewSummary): ReviewSummary = jpaRepository.save(summary)
}
