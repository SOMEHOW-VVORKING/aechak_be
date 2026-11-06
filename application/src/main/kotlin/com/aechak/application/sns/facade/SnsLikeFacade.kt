package com.aechak.application.sns.facade

import com.aechak.application.file.usecase.FileUseCase
import com.aechak.application.sns.port.view.SnsLikerView
import com.aechak.application.sns.service.CommentCommandService
import com.aechak.application.sns.service.CommentQueryService
import com.aechak.application.sns.service.FollowService
import com.aechak.application.sns.service.LikeService
import com.aechak.application.sns.service.PostAccessService
import com.aechak.application.sns.service.SnsUserListService
import com.aechak.application.sns.service.SnsVisibilityService
import com.aechak.application.sns.usecase.SnsLikeUseCase
import com.aechak.application.sns.usecase.command.LikeCommentCommand
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
    private val commentCommandService: CommentCommandService,
    private val commentQueryService: CommentQueryService,
    transactionManager: PlatformTransactionManager,
) : SnsLikeUseCase {
    private val tx =
        TransactionTemplate(transactionManager).apply {
            isolationLevel = TransactionDefinition.ISOLATION_READ_COMMITTED
        }

    // 본인 글과 뮤트한 작성자의 글에도 누를 수 있다
    override fun likePost(command: LikePostCommand): SnsLikeResult = toggle(command, likeService::likePost)

    // 끄기도 켜기와 같은 판정을 거쳐 볼 수 없게 된 게시물의 수를 내주지 않는다
    override fun unlikePost(command: LikePostCommand): SnsLikeResult = toggle(command, likeService::unlikePost)

    // 차단 관계인 사용자를 뺀 뒤 100명을 채우고 한 명 더 읽어 남은 사람이 있는지 본다. 뮤트한 사용자는 그대로 나온다
    override fun getPostLikers(command: LikePostCommand): SnsLikerListResult =
        tx.execute {
            val post = requireVisible(command.publicId, command.userId)
            likers(command.userId, likeService.countByPostIds(listOf(post.id)).getValue(post.id)) { hidden, limit ->
                snsUserListService.getPostLikers(post.id, hidden, limit)
            }
        }!!

    // 본인 댓글에도 누를 수 있다
    override fun likeComment(command: LikeCommentCommand): SnsLikeResult = toggleComment(command, likeService::likeComment)

    // 끄기도 켜기와 같은 판정을 거쳐 볼 수 없게 된 댓글의 수를 내주지 않는다
    override fun unlikeComment(command: LikeCommentCommand): SnsLikeResult = toggleComment(command, likeService::unlikeComment)

    override fun getCommentLikers(command: LikeCommentCommand): SnsLikerListResult =
        tx.execute {
            val commentId = requireVisibleComment(command.commentPublicId, command.userId)
            likers(command.userId, likeService.countByCommentIds(listOf(commentId)).getValue(commentId)) { hidden, limit ->
                snsUserListService.getCommentLikers(commentId, hidden, limit)
            }
        }!!

    /** 좋아요한 사람 한 화면. likeCount는 뺀 사용자도 센 전체 수다 */
    private fun likers(
        viewerId: Long,
        likeCount: Long,
        fetch: (hidden: Set<Long>, limit: Int) -> List<SnsLikerView>,
    ): SnsLikerListResult {
        val fetched = fetch(snsVisibilityService.findViewerFilter(viewerId), LIKERS_LIMIT + 1)
        val likers = fetched.take(LIKERS_LIMIT)
        val userIds = likers.map { it.userId }
        val followingIds = followService.findFollowingAmong(viewerId, userIds)
        val pets = petProfileUseCase.getDefaultPetSummaries(userIds).associateBy { it.userId }
        return SnsLikerListResult(
            likeCount = likeCount,
            hasMore = fetched.size > LIKERS_LIMIT,
            items =
                likers.map {
                    val isMe = it.userId == viewerId
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
    }

    /** 게시물 판정과 댓글 판정 뒤 댓글 좋아요를 바꾸고 같은 트랜잭션에서 지금의 수를 센다 */
    private fun toggleComment(
        command: LikeCommentCommand,
        change: (commentId: Long, userId: Long) -> Unit,
    ): SnsLikeResult =
        tx.execute {
            val commentId = requireVisibleComment(command.commentPublicId, command.userId)
            change(commentId, command.userId)
            SnsLikeResult(likeService.countByCommentIds(listOf(commentId)).getValue(commentId))
        }!!

    /** 없는 댓글이면 130700, 게시물 판정 뒤 보는 사람에게 보이지 않거나 안내 문구만 남은 댓글이면 130700이다 */
    private fun requireVisibleComment(
        commentPublicId: String,
        userId: Long,
    ): Long {
        val comment = commentCommandService.requireByPublicId(commentPublicId)
        val post = requireVisible(comment.post.publicId, userId)
        return commentQueryService.requireLikeTarget(commentQueryService.scopeOf(post, userId), commentPublicId)
    }

    /** 상세와 같은 판정 뒤 좋아요를 바꾸고 같은 트랜잭션에서 지금의 수를 센다 */
    private fun toggle(
        command: LikePostCommand,
        change: (postId: Long, userId: Long) -> Unit,
    ): SnsLikeResult =
        tx.execute {
            val post = requireVisible(command.publicId, command.userId)
            change(post.id, command.userId)
            SnsLikeResult(likeService.countByPostIds(listOf(post.id)).getValue(post.id))
        }!!

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
