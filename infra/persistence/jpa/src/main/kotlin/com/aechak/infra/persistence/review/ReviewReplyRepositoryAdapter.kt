package com.aechak.infra.persistence.review

import com.aechak.domain.review.reply.ReviewReply
import com.aechak.domain.review.reply.repository.DuplicateReviewReplyException
import com.aechak.domain.review.reply.repository.ReviewReplyRepository
import jakarta.persistence.LockModeType
import org.hibernate.exception.ConstraintViolationException
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository

interface ReviewReplyJpaRepository : JpaRepository<ReviewReply, Long> {
    fun findByReviewId(reviewId: Long): ReviewReply?

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select rr from ReviewReply rr where rr.reviewId = :reviewId")
    fun findByReviewIdForUpdate(
        @Param("reviewId") reviewId: Long,
    ): ReviewReply?

    @Modifying
    @Query("delete from ReviewReply rr where rr.reviewId = :reviewId")
    fun deleteByReviewId(
        @Param("reviewId") reviewId: Long,
    ): Int
}

@Repository
class ReviewReplyRepositoryAdapter(
    private val jpaRepository: ReviewReplyJpaRepository,
) : ReviewReplyRepository {
    override fun findByReviewId(reviewId: Long): ReviewReply? = jpaRepository.findByReviewId(reviewId)

    override fun findByReviewIdForUpdate(reviewId: Long): ReviewReply? = jpaRepository.findByReviewIdForUpdate(reviewId)

    /** UNIQUE 위반을 호출 지점에서 드러내려면 flush가 필요하다 — 커밋까지 미루면 변환할 자리를 놓친다. */
    override fun save(reviewReply: ReviewReply): ReviewReply =
        try {
            jpaRepository.saveAndFlush(reviewReply)
        } catch (e: DataIntegrityViolationException) {
            val violation =
                generateSequence<Throwable>(e) { it.cause }
                    .filterIsInstance<ConstraintViolationException>()
                    .firstOrNull()

            if (
                violation?.kind == ConstraintViolationException.ConstraintKind.UNIQUE &&
                violation.constraintName?.substringAfterLast('.') == ReviewReply.UK_REVIEW_ID
            ) {
                throw DuplicateReviewReplyException(e)
            }
            throw e
        }

    override fun delete(reviewReply: ReviewReply) = jpaRepository.delete(reviewReply)

    override fun deleteByReviewId(reviewId: Long): Int = jpaRepository.deleteByReviewId(reviewId)
}
