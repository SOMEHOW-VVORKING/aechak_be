package com.aechak.application.sns.service

import com.aechak.application.sns.usecase.result.CreatePostResult
import com.aechak.domain.sns.post.Post
import com.aechak.domain.sns.post.repository.PostRepository
import com.aechak.domain.sns.stats.repository.SnsUserStatsRepository
import org.springframework.stereotype.Service

@Service
class PostCommandService(
    private val postRepository: PostRepository,
    private val snsUserStatsRepository: SnsUserStatsRepository,
) {
    fun findPublicIdByIdempotencyKey(
        authorId: Long,
        idempotencyKey: String,
    ): String? = postRepository.findByAuthorIdAndIdempotencyKey(authorId, idempotencyKey)?.publicId

    /** 게시물과 미디어 행을 저장하고 작성자 게시물 수를 올린다 */
    fun create(
        authorId: Long,
        caption: String?,
        photoKeys: List<String>,
        petId: Long?,
        idempotencyKey: String,
    ): CreatePostResult {
        val post = postRepository.save(Post.create(authorId, caption, photoKeys, petId, idempotencyKey))
        snsUserStatsRepository.increasePostCount(authorId)
        return CreatePostResult(post.publicId)
    }
}
