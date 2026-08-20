package com.aechak.infra.persistence.review

import com.aechak.domain.review.review.Review
import com.aechak.domain.review.review.repository.ReviewRepository
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Repository

interface ReviewJpaRepository : JpaRepository<Review, Long>

@Repository
class ReviewRepositoryAdapter(
    private val jpaRepository: ReviewJpaRepository,
) : ReviewRepository {
    override fun findById(id: Long): Review? = jpaRepository.findByIdOrNull(id)

    override fun save(review: Review): Review = jpaRepository.save(review)
}
