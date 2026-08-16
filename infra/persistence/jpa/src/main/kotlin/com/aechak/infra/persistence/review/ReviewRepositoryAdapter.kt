package com.aechak.infra.persistence.review

import com.aechak.domain.review.review.Review
import com.aechak.domain.review.review.repository.DuplicateOrderItemReviewException
import com.aechak.domain.review.review.repository.ReviewRepository
import org.hibernate.exception.ConstraintViolationException
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.findByIdOrNull
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository

interface ReviewJpaRepository : JpaRepository<Review, Long> {
    fun existsByOrderItemId(orderItemId: Long): Boolean

    @Modifying
    @Query(
        value = """
            UPDATE reviews
            SET review_status = 'DELETED', deleted_at = NOW(6), updated_at = NOW(6)
            WHERE id = :id AND author_user_id = :userId AND review_status <> 'DELETED'
        """,
        nativeQuery = true,
    )
    fun markDeletedIfNotDeleted(
        @Param("id") id: Long,
        @Param("userId") userId: Long,
    ): Int
}

@Repository
class ReviewRepositoryAdapter(
    private val jpaRepository: ReviewJpaRepository,
) : ReviewRepository {
    override fun findById(id: Long): Review? = jpaRepository.findByIdOrNull(id)

    override fun save(review: Review): Review =
        try {
            jpaRepository.saveAndFlush(review)
        } catch (e: DataIntegrityViolationException) {
            val violation =
                generateSequence<Throwable>(e) { it.cause }
                    .filterIsInstance<ConstraintViolationException>()
                    .firstOrNull()

            if (
                violation?.kind == ConstraintViolationException.ConstraintKind.UNIQUE &&
                violation.constraintName?.substringAfterLast('.') == Review.UK_ORDER_ITEM_ID
            ) {
                throw DuplicateOrderItemReviewException(e)
            }
            throw e
        }

    override fun existsByOrderItemId(orderItemId: Long): Boolean = jpaRepository.existsByOrderItemId(orderItemId)

    override fun markDeletedIfNotDeleted(
        reviewId: Long,
        userId: Long,
    ): Int = jpaRepository.markDeletedIfNotDeleted(reviewId, userId)
}
