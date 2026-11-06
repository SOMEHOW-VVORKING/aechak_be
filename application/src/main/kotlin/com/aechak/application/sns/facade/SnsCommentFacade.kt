package com.aechak.application.sns.facade

import com.aechak.application.moderation.usecase.ModerationUseCase
import com.aechak.application.moderation.usecase.result.ModerationDecision
import com.aechak.application.sns.commentdeletion.service.CommentDeletionService
import com.aechak.application.sns.service.CommentCommandService
import com.aechak.application.sns.service.CommentQueryService
import com.aechak.application.sns.service.PostAccessService
import com.aechak.application.sns.support.CommentCursorCodec
import com.aechak.application.sns.usecase.SnsCommentUseCase
import com.aechak.application.sns.usecase.command.CreateCommentCommand
import com.aechak.application.sns.usecase.command.UpdateCommentCommand
import com.aechak.application.sns.usecase.query.SnsCommentListQuery
import com.aechak.application.sns.usecase.result.SnsCommentItemResult
import com.aechak.application.support.CursorPageResult
import com.aechak.application.user.user.usecase.UserUseCase
import com.aechak.common.error.BusinessException
import com.aechak.domain.sns.comment.Comment
import com.aechak.domain.sns.comment.enums.CommentDeletionReason
import com.aechak.domain.sns.comment.enums.CommentStatus
import com.aechak.domain.sns.error.SnsErrorCode
import com.aechak.domain.sns.post.Post
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.stereotype.Service
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.TransactionDefinition
import org.springframework.transaction.annotation.Transactional
import org.springframework.transaction.support.TransactionTemplate

/** SNS 댓글 진입점. 금지어 판정은 댓글 쓰기만 쓰므로 좋아요와 나눠 둔다. 금지어 판정은 트랜잭션 밖에서 한다 */
@Service
class SnsCommentFacade(
    private val commentQueryService: CommentQueryService,
    private val commentCommandService: CommentCommandService,
    private val commentDeletionService: CommentDeletionService,
    private val postAccessService: PostAccessService,
    private val moderationUseCase: ModerationUseCase,
    private val userUseCase: UserUseCase,
    transactionManager: PlatformTransactionManager,
) : SnsCommentUseCase {
    private val tx =
        TransactionTemplate(transactionManager).apply {
            isolationLevel = TransactionDefinition.ISOLATION_READ_COMMITTED
        }

    private val readTx =
        TransactionTemplate(transactionManager).apply {
            isReadOnly = true
        }

    @Transactional(readOnly = true)
    override fun getComments(query: SnsCommentListQuery): CursorPageResult<SnsCommentItemResult> {
        val post = requireVisiblePost(query.postPublicId, query.viewerId)
        val lastRootId = query.cursor?.let { CommentCursorCodec.decode(it, post.publicId) }
        return commentQueryService.getPage(commentQueryService.scopeOf(post, query.viewerId), post.publicId, lastRootId, query.size)
    }

    override fun createComment(command: CreateCommentCommand): SnsCommentItemResult {
        findIdempotent(command)?.let { return it }
        val content = moderate(Comment.normalizeContent(command.content))
        return try {
            tx.execute {
                val post = requireVisiblePost(command.postPublicId, command.userId)
                // 대댓글에 단 답글은 그 최상위 댓글 아래 붙는다. 차단 확인은 잠금 읽기가 아니다
                val root =
                    command.parentPublicId?.let {
                        val target = commentQueryService.requireReplyTarget(commentQueryService.scopeOf(post, command.userId), it)
                        commentCommandService.lockReplyRoot(target.parentId ?: target.id)
                    }
                val comment = commentCommandService.write(post, root, command.userId, content, command.idempotencyKey)
                commentQueryService.itemOf(comment, command.userId)
            }!!
        } catch (e: DataIntegrityViolationException) {
            // 동시 요청은 사전 조회를 둘 다 통과한다. (작성자, 게시물, 멱등키) UNIQUE가 심판이고 진 쪽은 먼저 생긴 댓글을 돌려준다.
            // try가 tx.execute를 감싸야 함. 안에서 잡으면 롤백 전용 마킹 때문에 커밋에서 다시 터짐
            findIdempotent(command) ?: throw e
        }
    }

    override fun updateComment(command: UpdateCommentCommand): SnsCommentItemResult {
        // 규칙은 댓글 조회보다 먼저 본다. 없는 댓글이어도 규칙을 어긴 수정은 404가 아니라 400이다
        val content = moderate(Comment.normalizeContent(command.content))
        return tx.execute {
            val comment = commentCommandService.lockForAuthor(command.commentPublicId)
            requireVisiblePost(comment.post.publicId, command.userId)
            if (comment.authorId != command.userId) throw BusinessException(SnsErrorCode.SNS_COMMENT_NOT_AUTHOR)
            if (comment.status == CommentStatus.DELETED) throw BusinessException(SnsErrorCode.SNS_COMMENT_UNAVAILABLE)
            comment.edit(content)
            commentQueryService.itemOf(comment, command.userId)
        }!!
    }

    // 게시물이 지워졌거나 게시물 작성자와 차단 관계여도 자기 댓글은 지운다. 무본문이라 게시물 정보가 드러나지 않는다
    override fun deleteComment(
        userId: Long,
        commentPublicId: String,
    ) {
        tx.execute {
            val comment = commentCommandService.lockForAuthor(commentPublicId)
            if (comment.authorId != userId) throw BusinessException(SnsErrorCode.SNS_COMMENT_NOT_AUTHOR)
            commentDeletionService.delete(comment, CommentDeletionReason.AUTHOR)
        }
    }

    /** 게시물 판정은 상세와 같다. 비로그인과 작성자 본인은 차단 판정을 건너뛴다 */
    private fun requireVisiblePost(
        publicId: String,
        viewerId: Long?,
    ): Post {
        val post = postAccessService.requirePublished(publicId)
        postAccessService.requireActiveAuthor(userUseCase.findPublicProfileById(post.authorId))
        postAccessService.requireNotBlocked(post.authorId, viewerId)
        return post
    }

    // 최상위 댓글을 LAZY로 읽어 항목을 만들므로 조회와 조립을 한 트랜잭션에서 한다
    private fun findIdempotent(command: CreateCommentCommand): SnsCommentItemResult? =
        readTx.execute {
            commentCommandService
                .findByIdempotencyKey(command.userId, command.postPublicId, command.idempotencyKey)
                ?.let { commentQueryService.itemOf(it, command.userId) }
        }

    // 차단이면 130704이고 마스킹이면 가린 글을 저장한다
    private fun moderate(content: String): String =
        when (val decision = moderationUseCase.decide(content)) {
            is ModerationDecision.Keep -> content
            is ModerationDecision.Mask -> decision.displayContent
            is ModerationDecision.Block -> throw BusinessException(SnsErrorCode.SNS_COMMENT_BLOCKED)
        }
}
