package com.aechak.application.sns.service

import com.aechak.application.sns.usecase.result.CreatePostResult
import com.aechak.common.error.BusinessException
import com.aechak.domain.sns.error.SnsErrorCode
import com.aechak.domain.sns.post.Post
import com.aechak.domain.sns.post.enums.PostStatus
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

    /** 작성자 본인의 게시물을 잠가 캡션을 바꾼다. 없거나 지운 게시물이면 130400, 작성자가 아니면 130401이다 */
    fun update(
        publicId: String,
        userId: Long,
        caption: String?,
    ) {
        val post = postRepository.findByPublicIdForUpdate(publicId) ?: throw BusinessException(SnsErrorCode.SNS_POST_UNAVAILABLE)
        if (post.authorId != userId) throw BusinessException(SnsErrorCode.SNS_POST_NOT_AUTHOR)
        if (post.status == PostStatus.DELETED) throw BusinessException(SnsErrorCode.SNS_POST_UNAVAILABLE)
        post.update(caption)
    }
}
