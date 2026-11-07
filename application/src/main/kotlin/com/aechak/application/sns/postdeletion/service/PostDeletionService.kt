package com.aechak.application.sns.postdeletion.service

import com.aechak.common.error.BusinessException
import com.aechak.domain.sns.error.SnsErrorCode
import com.aechak.domain.sns.hashtag.repository.HashtagRepository
import com.aechak.domain.sns.post.Post
import com.aechak.domain.sns.post.enums.PostDeletionReason
import com.aechak.domain.sns.post.repository.PostRepository
import com.aechak.domain.sns.stats.repository.SnsUserStatsRepository
import org.springframework.stereotype.Service

@Service
class PostDeletionService(
    private val postRepository: PostRepository,
    private val hashtagRepository: HashtagRepository,
    private val snsUserStatsRepository: SnsUserStatsRepository,
) {
    /** 게시물 행을 잠가 읽는다. 없으면 130400이다 */
    fun lock(publicId: String): Post =
        postRepository.findByPublicIdForUpdate(publicId) ?: throw BusinessException(SnsErrorCode.SNS_POST_UNAVAILABLE)

    /** 작성자 본인의 게시물을 잠가 읽는다. 없으면 130400, 작성자가 아니면 130401이다 */
    fun lockOwnedBy(
        publicId: String,
        userId: Long,
    ): Post {
        val post = lock(publicId)
        if (post.authorId != userId) throw BusinessException(SnsErrorCode.SNS_POST_NOT_AUTHOR)
        return post
    }

    /** 작성자의 게시된 게시물을 모두 잠가 읽는다 */
    fun lockPublishedOf(authorId: Long): List<Post> = postRepository.findPublishedByAuthorIdForUpdate(authorId)

    /**
     * 잠근 게시물을 지우고 태그 게시글 수와 작성자 게시물 수를 내린 뒤 지울 미디어 파일 키를 돌려준다.
     * 이미 지운 게시물이면 상태와 수를 그대로 두고 파일 키만 다시 돌려준다.
     */
    fun delete(
        post: Post,
        reason: PostDeletionReason,
    ): List<String> {
        // 지우면 태그 연결이 비므로 태그 id를 먼저 읽는다
        val hashtagIds = post.hashtags.map { it.hashtagId }
        if (post.delete(reason)) {
            // 태그 행을 작성자 통계 행보다 먼저 잠근다. 게시와 순서가 엇갈리면 서로 교착한다
            hashtagRepository.adjustCounts(removed = hashtagRepository.findAllById(hashtagIds).map { it.name })
            snsUserStatsRepository.decreasePostCount(post.authorId)
        }
        // 미디어는 지연 로딩이라 커밋 뒤에 읽으면 LazyInitializationException이 나서 트랜잭션 안에서 모은다
        return post.media.flatMap { listOfNotNull(it.storageKey, it.thumbnailKey) }
    }
}
