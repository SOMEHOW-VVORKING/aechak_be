package com.aechak.infra.persistence.review

import com.aechak.domain.review.review.Review
import com.aechak.domain.review.review.repository.DuplicateOrderItemReviewException
import com.aechak.domain.review.review.repository.ReviewRepository
import jakarta.persistence.LockModeType
import org.hibernate.exception.ConstraintViolationException
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.findByIdOrNull
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository

interface ReviewJpaRepository : JpaRepository<Review, Long> {
    fun existsByOrderItemId(orderItemId: Long): Boolean

    // 상속받은 findById에는 @Lock을 걸 수 없어 잠금 전용 메서드를 따로 둔다
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from Review r where r.id = :id")
    fun findByIdForUpdate(
        @Param("id") id: Long,
    ): Review?
}

@Repository
class ReviewRepositoryAdapter(
    private val jpaRepository: ReviewJpaRepository,
) : ReviewRepository {
    override fun findById(id: Long): Review? = jpaRepository.findByIdOrNull(id)

    override fun findByIdForUpdate(id: Long): Review? = jpaRepository.findByIdForUpdate(id)

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
}
