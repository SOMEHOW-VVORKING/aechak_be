package com.aechak.infra.persistence.sns.comment

import com.aechak.domain.sns.comment.Comment
import com.aechak.domain.sns.comment.repository.CommentRepository
import jakarta.persistence.LockModeType
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository

interface CommentJpaRepository : JpaRepository<Comment, Long> {
    fun findByPublicId(publicId: String): Comment?

    @Query("select c from Comment c where c.authorId = :authorId and c.post.id = :postId and c.idempotencyKey = :key")
    fun findByIdempotencyKey(
        @Param("authorId") authorId: Long,
        @Param("postId") postId: Long,
        @Param("key") idempotencyKey: String,
    ): Comment?

    // 쓰기 경로 전용. 단일 테이블 조회로 둬서 댓글 행만 잠근다
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from Comment c where c.publicId = :publicId")
    fun findForUpdateByPublicId(
        @Param("publicId") publicId: String,
    ): Comment?

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from Comment c where c.id = :id")
    fun findForUpdateById(
        @Param("id") id: Long,
    ): Comment?
}

@Repository
class CommentRepositoryAdapter(
    private val jpaRepository: CommentJpaRepository,
) : CommentRepository {
    override fun save(comment: Comment): Comment = jpaRepository.save(comment)

    override fun findByPublicId(publicId: String): Comment? = jpaRepository.findByPublicId(publicId)

    override fun findByPublicIdForUpdate(publicId: String): Comment? = jpaRepository.findForUpdateByPublicId(publicId)

    override fun findByIdForUpdate(id: Long): Comment? = jpaRepository.findForUpdateById(id)

    override fun findByIdempotencyKey(
        authorId: Long,
        postId: Long,
        idempotencyKey: String,
    ): Comment? = jpaRepository.findByIdempotencyKey(authorId, postId, idempotencyKey)
}
