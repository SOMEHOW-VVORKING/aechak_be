package com.aechak.application.sns.facade

import com.aechak.application.file.usecase.FileUseCase
import com.aechak.application.ratelimit.usecase.RateLimitUseCase
import com.aechak.application.sns.service.FollowService
import com.aechak.application.sns.service.LikeService
import com.aechak.application.sns.service.PostAccessService
import com.aechak.application.sns.service.SnsUserListService
import com.aechak.application.sns.service.SnsVisibilityService
import com.aechak.application.sns.support.SnsRateLimitRule
import com.aechak.application.sns.usecase.SnsLikeUseCase
import com.aechak.application.sns.usecase.command.LikePostCommand
import com.aechak.application.sns.usecase.result.SnsLikeResult
import com.aechak.application.sns.usecase.result.SnsLikerItemResult
import com.aechak.application.sns.usecase.result.SnsLikerListResult
import com.aechak.application.user.pet.usecase.PetProfileUseCase
import com.aechak.application.user.user.usecase.UserUseCase
import com.aechak.domain.sns.post.Post
import org.springframework.stereotype.Service
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.TransactionDefinition
import org.springframework.transaction.support.TransactionTemplate

/** SNS 좋아요 진입점. 금지어 판정을 쓰지 않아 게시물 쪽 SnsPostFacade와 나눠 둔다 */
@Service
class SnsLikeFacade(
    private val postAccessService: PostAccessService,
    private val likeService: LikeService,
    private val userUseCase: UserUseCase,
    private val snsVisibilityService: SnsVisibilityService,
    private val snsUserListService: SnsUserListService,
    private val followService: FollowService,
    private val petProfileUseCase: PetProfileUseCase,
    private val fileUseCase: FileUseCase,
    private val rateLimitUseCase: RateLimitUseCase,
    transactionManager: PlatformTransactionManager,
) : SnsLikeUseCase {
    private val tx =
        TransactionTemplate(transactionManager).apply {
            isolationLevel = TransactionDefinition.ISOLATION_READ_COMMITTED
        }

    // 본인 글과 뮤트한 작성자의 글에도 누를 수 있다
    override fun likePost(command: LikePostCommand): SnsLikeResult = toggle(command, SnsRateLimitRule.LIKE_ON, likeService::likePost)

    // 끄기도 켜기와 같은 판정을 거쳐 볼 수 없게 된 게시물의 수를 내주지 않는다
    override fun unlikePost(command: LikePostCommand): SnsLikeResult = toggle(command, SnsRateLimitRule.LIKE_OFF, likeService::unlikePost)

    // 차단 관계인 사용자를 뺀 뒤 100명을 채우고 한 명 더 읽어 남은 사람이 있는지 본다. 뮤트한 사용자는 그대로 나온다
    override fun getPostLikers(command: LikePostCommand): SnsLikerListResult =
        tx.execute {
            val post = requireVisible(command.publicId, command.userId)
            val hidden = snsVisibilityService.findViewerFilter(command.userId)
            val fetched = snsUserListService.getPostLikers(post.id, hidden, LIKERS_LIMIT + 1)
            val likers = fetched.take(LIKERS_LIMIT)
            val userIds = likers.map { it.userId }
            val followingIds = followService.findFollowingAmong(command.userId, userIds)
            val pets = petProfileUseCase.getDefaultPetSummaries(userIds).associateBy { it.userId }
            SnsLikerListResult(
                likeCount = likeService.countByPostIds(listOf(post.id)).getValue(post.id),
                hasMore = fetched.size > LIKERS_LIMIT,
                items =
                    likers.map {
                        val isMe = it.userId == command.userId
                        SnsLikerItemResult(
                            publicId = it.publicId,
                            nickname = it.nickname,
                            profileImageUrl = fileUseCase.resolveMediaUrl(it.profileImageKey),
                            pet = pets[it.userId],
                            isMe = isMe,
                            following = !isMe && it.userId in followingIds,
                        )
                    },
            )
        }!!

    /** 빈도 제한과 상세와 같은 판정 뒤 좋아요를 바꾸고 같은 트랜잭션에서 지금의 수를 센다 */
    private fun toggle(
        command: LikePostCommand,
        rule: SnsRateLimitRule,
        change: (postId: Long, userId: Long) -> Unit,
    ): SnsLikeResult {
        rateLimitUseCase.check(command.userId, rule)
        return tx.execute {
            val post = requireVisible(command.publicId, command.userId)
            change(post.id, command.userId)
            SnsLikeResult(likeService.countByPostIds(listOf(post.id)).getValue(post.id))
        }!!
    }

    private fun requireVisible(
        publicId: String,
        userId: Long,
    ): Post {
        val post = postAccessService.requirePublished(publicId)
        postAccessService.requireActiveAuthor(userUseCase.findPublicProfileById(post.authorId))
        postAccessService.requireNotBlocked(post.authorId, userId)
        return post
    }

    companion object {
        private const val LIKERS_LIMIT = 100
    }
}
