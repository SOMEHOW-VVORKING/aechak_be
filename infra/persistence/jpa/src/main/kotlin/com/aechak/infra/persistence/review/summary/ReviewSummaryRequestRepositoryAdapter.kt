package com.aechak.infra.persistence.review.summary

import com.aechak.domain.review.summary.ReviewSummaryRequest
import com.aechak.domain.review.summary.repository.ReviewSummaryRequestRepository
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Repository

interface ReviewSummaryRequestJpaRepository : JpaRepository<ReviewSummaryRequest, Long>

@Repository
class ReviewSummaryRequestRepositoryAdapter(
    private val jpaRepository: ReviewSummaryRequestJpaRepository,
) : ReviewSummaryRequestRepository {
    override fun findByProductId(productId: Long): ReviewSummaryRequest? = jpaRepository.findByIdOrNull(productId)

    override fun save(request: ReviewSummaryRequest): ReviewSummaryRequest = jpaRepository.save(request)
}
