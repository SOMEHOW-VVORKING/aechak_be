package com.aechak.application.sns.service

import com.aechak.application.sns.port.view.SnsPairStateView
import com.aechak.application.sns.usecase.result.SnsPostAuthorResult
import com.aechak.application.user.user.usecase.result.UserPublicProfileResult
import com.aechak.common.error.BusinessException
import com.aechak.domain.sns.error.SnsErrorCode
import com.aechak.domain.sns.post.Post
import com.aechak.domain.sns.post.enums.PostStatus
import com.aechak.domain.sns.post.repository.PostRepository
import com.aechak.domain.user.user.enums.UserStatus
import org.springframework.stereotype.Service

/** 보는 사람이 게시물을 볼 수 있는지 판정한다. 상세와 이후 피드, 좋아요, 댓글이 같은 판정을 쓴다 */
@Service
class PostAccessService(
    private val postRepository: PostRepository,
    private val snsVisibilityService: SnsVisibilityService,
) {
    /** 없는 게시물과 지운 게시물은 130400이다. publicId 형식은 따로 검사하지 않는다 */
    fun requirePublished(publicId: String): Post {
        val post = postRepository.findByPublicId(publicId)
        if (post == null || post.status == PostStatus.DELETED) throw BusinessException(SnsErrorCode.SNS_POST_UNAVAILABLE)
        return post
    }

    /** 프로필 행이 없는 작성자(탈퇴)와 영구 정지 작성자의 게시물은 130400이다 */
    fun requireActiveAuthor(author: UserPublicProfileResult?): SnsPostAuthorResult {
        if (author?.status == UserStatus.BANNED) throw BusinessException(SnsErrorCode.SNS_POST_UNAVAILABLE)
        val nickname = author?.nickname ?: throw BusinessException(SnsErrorCode.SNS_POST_UNAVAILABLE)
        return SnsPostAuthorResult(author.publicId, nickname, author.profileImageUrl)
    }

    /**
     * 나를 차단한 작성자(서로 차단 포함)의 게시물은 130400, 내가 차단한 작성자의 게시물은 130001이다.
     * 비로그인과 작성자 본인은 관계를 읽지 않는다.
     */
    fun requireNotBlocked(
        authorId: Long,
        viewerId: Long?,
    ): SnsPairStateView {
        val pair = snsVisibilityService.findPairState(viewerId, authorId)
        if (pair.blockedByTarget) throw BusinessException(SnsErrorCode.SNS_POST_UNAVAILABLE)
        if (pair.blockedByMe) throw BusinessException(SnsErrorCode.SNS_USER_BLOCKED_BY_VIEWER)
        return pair
    }
}
