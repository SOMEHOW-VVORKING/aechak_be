package com.aechak.infra.persistence.review

import com.aechak.domain.review.review.Review
import com.aechak.domain.review.review.repository.ReviewRepository
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.findByIdOrNull
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository

interface ReviewJpaRepository : JpaRepository<Review, Long> {
    // 이미 DELETED면 삭제 처리된 행의 개수가 0행
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

    override fun markDeletedIfNotDeleted(
        reviewId: Long,
        userId: Long,
    ) {
        jpaRepository.markDeletedIfNotDeleted(reviewId, userId)
    }
}
