package com.aechak.infra.persistence.sns.post

import com.aechak.domain.sns.post.Post
import com.aechak.domain.sns.post.enums.PostStatus
import com.aechak.domain.sns.post.repository.PostRepository
import jakarta.persistence.LockModeType
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository

interface PostJpaRepository : JpaRepository<Post, Long> {
    fun findByPublicId(publicId: String): Post?

    fun findByAuthorIdAndIdempotencyKey(
        authorId: Long,
        idempotencyKey: String,
    ): Post?

    // 쓰기 경로 전용. 단일 테이블 조회로 둬서 게시물 행만 잠근다.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from Post p where p.publicId = :publicId")
    fun findForUpdateByPublicId(
        @Param("publicId") publicId: String,
    ): Post?

    // 쓰기 경로 전용. ix_sns_posts_author 순서로 게시물 행만 잠근다
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from Post p where p.authorId = :authorId and p.status = :status")
    fun findForUpdateByAuthorIdAndStatus(
        @Param("authorId") authorId: Long,
        @Param("status") status: PostStatus,
    ): List<Post>
}

@Repository
class PostRepositoryAdapter(
    private val jpaRepository: PostJpaRepository,
) : PostRepository {
    override fun save(post: Post): Post = jpaRepository.save(post)

    override fun findByPublicId(publicId: String): Post? = jpaRepository.findByPublicId(publicId)

    override fun findByPublicIdForUpdate(publicId: String): Post? = jpaRepository.findForUpdateByPublicId(publicId)

    override fun findByAuthorIdAndIdempotencyKey(
        authorId: Long,
        idempotencyKey: String,
    ): Post? = jpaRepository.findByAuthorIdAndIdempotencyKey(authorId, idempotencyKey)

    override fun findPublishedByAuthorIdForUpdate(authorId: Long): List<Post> =
        jpaRepository.findForUpdateByAuthorIdAndStatus(authorId, PostStatus.PUBLISHED)
}
