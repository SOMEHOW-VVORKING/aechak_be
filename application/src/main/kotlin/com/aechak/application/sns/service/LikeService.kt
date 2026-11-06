package com.aechak.application.sns.service

import com.aechak.domain.sns.like.repository.CommentLikeRepository
import com.aechak.domain.sns.like.repository.PostLikeRepository
import org.springframework.stereotype.Service

/** 좋아요 행을 넣고 지우고 센다. 좋아요 수를 저장하지 않아 통계 행을 잠그지 않는다 */
@Service
class LikeService(
    private val postLikeRepository: PostLikeRepository,
    private val commentLikeRepository: CommentLikeRepository,
) {
    fun likePost(
        postId: Long,
        userId: Long,
    ) = postLikeRepository.insertIfAbsent(postId, userId)

    fun unlikePost(
        postId: Long,
        userId: Long,
    ) {
        postLikeRepository.delete(postId, userId)
    }

    /** 게시물 id마다 좋아요 수. 좋아요가 없는 게시물은 0이다 */
    fun countByPostIds(postIds: Collection<Long>): Map<Long, Long> = postLikeRepository.countByPostIds(postIds)

    fun likeComment(
        commentId: Long,
        userId: Long,
    ) = commentLikeRepository.insertIfAbsent(commentId, userId)

    fun unlikeComment(
        commentId: Long,
        userId: Long,
    ) {
        commentLikeRepository.delete(commentId, userId)
    }

    /** 댓글 id마다 좋아요 수. 좋아요가 없는 댓글은 0이다 */
    fun countByCommentIds(commentIds: Collection<Long>): Map<Long, Long> = commentLikeRepository.countByCommentIds(commentIds)

    /** commentIds 중 viewer가 좋아요한 댓글 id. 비로그인은 빈 집합이다 */
    fun findLikedCommentIdsAmong(
        viewerId: Long?,
        commentIds: Collection<Long>,
    ): Set<Long> = viewerId?.let { commentLikeRepository.findLikedCommentIdsAmong(it, commentIds) } ?: emptySet()
}
