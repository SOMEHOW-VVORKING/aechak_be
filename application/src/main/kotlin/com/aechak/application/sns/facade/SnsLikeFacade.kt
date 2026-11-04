package com.aechak.application.sns.facade

import com.aechak.application.sns.service.LikeService
import com.aechak.application.sns.service.PostAccessService
import com.aechak.application.sns.usecase.SnsLikeUseCase
import com.aechak.application.sns.usecase.command.LikePostCommand
import com.aechak.application.sns.usecase.result.SnsLikeResult
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
}
