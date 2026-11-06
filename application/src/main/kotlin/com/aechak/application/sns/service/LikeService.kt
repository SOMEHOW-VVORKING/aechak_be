package com.aechak.application.sns.service

import com.aechak.domain.sns.like.repository.PostLikeRepository
import org.springframework.stereotype.Service

/** 좋아요 행을 넣고 지우고 센다. 좋아요 수를 저장하지 않아 통계 행을 잠그지 않는다 */
@Service
class LikeService(
    private val postLikeRepository: PostLikeRepository,
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
}
