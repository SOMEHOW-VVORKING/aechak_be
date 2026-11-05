package com.aechak.application.sns.service

import com.aechak.application.sns.usecase.result.CreatePostResult
import com.aechak.common.error.BusinessException
import com.aechak.domain.sns.error.SnsErrorCode
import com.aechak.domain.sns.hashtag.repository.HashtagRepository
import com.aechak.domain.sns.post.Post
import com.aechak.domain.sns.post.enums.PostStatus
import com.aechak.domain.sns.post.repository.PostRepository
import com.aechak.domain.sns.stats.repository.SnsUserStatsRepository
import org.springframework.stereotype.Service

@Service
class PostCommandService(
    private val postRepository: PostRepository,
    private val hashtagRepository: HashtagRepository,
    private val snsUserStatsRepository: SnsUserStatsRepository,
) {
    fun findPublicIdByIdempotencyKey(
        authorId: Long,
        idempotencyKey: String,
    ): String? = postRepository.findByAuthorIdAndIdempotencyKey(authorId, idempotencyKey)?.publicId

    /** 태그 게시글 수를 올리고 게시물과 미디어, 태그 연결을 저장한 뒤 작성자 게시물 수를 올린다 */
    fun create(
        authorId: Long,
        caption: String?,
        photoKeys: List<String>,
        video: Post.VideoSpec?,
        hashtags: List<String>,
        petId: Long?,
        idempotencyKey: String,
    ): CreatePostResult {
        // 태그 행을 작성자 통계 행보다 먼저 잠근다. 수정, 삭제와 순서가 엇갈리면 서로 교착한다
        hashtagRepository.adjustCounts(added = hashtags)
        val hashtagIds = hashtagRepository.findAllByNameIn(hashtags).associate { it.name to it.id }
        val post =
            postRepository.save(Post.create(authorId, caption, photoKeys, video, hashtags.map(hashtagIds::getValue), petId, idempotencyKey))
        snsUserStatsRepository.increasePostCount(authorId)
        return CreatePostResult(post.publicId)
    }

    /**
     * 작성자 본인의 게시물을 잠가 캡션과 태그를 바꾸고 태그 게시글 수를 맞춘다. 없거나 지운 게시물이면 130400, 작성자가 아니면 130401이다.
     * 지금 달린 태그는 게시물 행을 잠근 뒤에 읽는다. 잠그기 전에 읽으면 같은 게시물을 동시에 고칠 때 같은 태그 수를 두 번 내린다.
     */
    fun update(
        publicId: String,
        userId: Long,
        caption: String?,
        hashtags: List<String>,
    ) {
        val post = postRepository.findByPublicIdForUpdate(publicId) ?: throw BusinessException(SnsErrorCode.SNS_POST_UNAVAILABLE)
        if (post.authorId != userId) throw BusinessException(SnsErrorCode.SNS_POST_NOT_AUTHOR)
        if (post.status == PostStatus.DELETED) throw BusinessException(SnsErrorCode.SNS_POST_UNAVAILABLE)
        // 거부할 수정이 태그 행을 잠그거나 새 태그 행을 만들지 않게 태그 수를 바꾸기 전에 확인한다
        post.validateUpdate(caption)
        val current = hashtagRepository.findAllById(post.hashtags.map { it.hashtagId }).map { it.name }
        // 두 목록에 함께 든 태그는 adjustCounts가 건너뛰므로 빠진 태그와 새 태그의 수만 바뀐다
        hashtagRepository.adjustCounts(added = hashtags, removed = current)
        val hashtagIds = hashtagRepository.findAllByNameIn(hashtags).associate { it.name to it.id }
        post.update(caption, hashtags.map(hashtagIds::getValue))
    }
}
