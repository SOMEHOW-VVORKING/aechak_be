package com.aechak.application.sns.service

import com.aechak.application.file.usecase.FileUseCase
import com.aechak.application.sns.port.SnsCommentQueryPort
import com.aechak.application.sns.port.SnsCommentScope
import com.aechak.application.sns.port.view.SnsCommentRefView
import com.aechak.application.sns.port.view.SnsCommentView
import com.aechak.application.sns.support.CommentCursorCodec
import com.aechak.application.sns.usecase.result.SnsCommentItemResult
import com.aechak.application.sns.usecase.result.SnsCommentItemStatus
import com.aechak.application.sns.usecase.result.SnsPostAuthorResult
import com.aechak.application.support.CursorPageResult
import com.aechak.application.support.CursorPageSize
import com.aechak.application.user.user.usecase.UserUseCase
import com.aechak.common.error.BusinessException
import com.aechak.domain.sns.comment.Comment
import com.aechak.domain.sns.comment.enums.CommentDeletionReason
import com.aechak.domain.sns.comment.enums.CommentStatus
import com.aechak.domain.sns.error.SnsErrorCode
import com.aechak.domain.sns.post.Post
import org.springframework.stereotype.Service
import java.time.LocalDateTime

/** 댓글 읽기. 보는 사람 기준 거르기는 포트의 조건식이 하고 이 서비스는 항목을 조립한다 */
@Service
class CommentQueryService(
    private val commentQueryPort: SnsCommentQueryPort,
    private val snsVisibilityService: SnsVisibilityService,
    private val fileUseCase: FileUseCase,
    private val userUseCase: UserUseCase,
) {
    fun scopeOf(
        post: Post,
        viewerId: Long?,
    ): SnsCommentScope =
        SnsCommentScope(
            postId = post.id,
            postAuthorId = post.authorId,
            viewerId = viewerId,
            hidden = snsVisibilityService.findViewerFilter(viewerId),
            muted = snsVisibilityService.findMutedIds(viewerId),
        )

    /** 상세와 피드 카드의 댓글 수. 섀도우 처리된 사용자에게만 자기 숨긴 댓글과 그 아래 대댓글이 더해진다 */
    fun countVisible(
        postIds: Collection<Long>,
        viewerId: Long?,
    ): Map<Long, Long> = commentQueryPort.countVisible(postIds, viewerId)

    /** 보는 사람에게 보이는 이 게시물의 댓글을 답글 대상으로 찾는다. 없거나 보이지 않으면 130700이다 */
    fun requireReplyTarget(
        scope: SnsCommentScope,
        commentPublicId: String,
    ): SnsCommentRefView =
        commentQueryPort.findVisibleRef(scope, commentPublicId) ?: throw BusinessException(SnsErrorCode.SNS_COMMENT_UNAVAILABLE)

    /** 최상위 댓글 size개와 그 대댓글을 스레드 순서로 이어 붙인 한 페이지 */
    fun getPage(
        scope: SnsCommentScope,
        postPublicId: String,
        lastRootId: Long?,
        size: Int,
    ): CursorPageResult<SnsCommentItemResult> {
        val roots =
            CursorPageResult.of(commentQueryPort.findRootPage(scope, lastRootId, CursorPageSize.fetchLimit(size)), size) {
                CommentCursorCodec.encode(postPublicId, it.id)
            }
        val replies = commentQueryPort.findReplies(scope, roots.items.map { it.id }).groupBy { it.parentId }
        val items =
            roots.items.flatMap { root ->
                val rootItem = toItem(root, null, null, scope.viewerId)
                listOf(rootItem) + replies[root.id].orEmpty().map { toItem(it, root.publicId, rootItem.status, scope.viewerId) }
            }
        return CursorPageResult(items, null, roots.nextCursor, roots.hasNext)
    }

    /** rootStatus는 대댓글이면 최상위 댓글 항목의 상태이고 최상위 댓글이면 null이다 */
    fun toItem(
        view: SnsCommentView,
        parentPublicId: String?,
        rootStatus: SnsCommentItemStatus?,
        viewerId: Long?,
    ): SnsCommentItemResult {
        val author =
            view.authorNickname?.let {
                SnsPostAuthorResult(
                    view.authorPublicId!!,
                    it,
                    fileUseCase.resolveMediaUrl(view.authorProfileImageKey),
                )
            }
        return item(
            publicId = view.publicId,
            parentPublicId = parentPublicId,
            status = statusOf(view.status, view.deletionReason, author != null),
            rootStatus = rootStatus,
            author = author,
            content = view.content,
            edited = view.editedAt != null,
            isMine = view.authorId == viewerId,
            createdAt = view.createdAt,
        )
    }

    /**
     * 쓰기 경로의 항목. 사용자 표를 조인하지 않고 UserUseCase로 작성자 표시 정보를 읽는다.
     * 최상위 댓글을 LAZY로 읽으므로 트랜잭션 안에서 부른다.
     */
    fun itemOf(
        comment: Comment,
        viewerId: Long,
    ): SnsCommentItemResult {
        val root = comment.parent
        val author = authorOf(comment.authorId)
        return item(
            publicId = comment.publicId,
            parentPublicId = root?.publicId,
            status = statusOf(comment.status, comment.deletionReason, author != null),
            rootStatus = root?.let { statusOf(it.status, it.deletionReason, authorOf(it.authorId) != null) },
            author = author,
            content = comment.content,
            edited = comment.editedAt != null,
            isMine = comment.authorId == viewerId,
            createdAt = comment.createdAt,
        )
    }

    private fun authorOf(userId: Long): SnsPostAuthorResult? =
        userUseCase.findPublicProfileById(userId)?.let { profile ->
            profile.nickname?.let { SnsPostAuthorResult(profile.publicId, it, profile.profileImageUrl) }
        }

    /** hasProfile이 false면 프로필 행이 없는 작성자(탈퇴)다 */
    private fun statusOf(
        status: CommentStatus,
        reason: CommentDeletionReason?,
        hasProfile: Boolean,
    ): SnsCommentItemStatus =
        when {
            status == CommentStatus.DELETED && reason == CommentDeletionReason.WITHDRAWAL -> SnsCommentItemStatus.WITHDRAWN

            status == CommentStatus.DELETED -> SnsCommentItemStatus.DELETED

            // shortcut: SNS-T18 전에 탈퇴한 작성자의 댓글은 프로필 행이 없어 안내 문구로 바꿔 보여주고 수에는 남긴다.
            // SNS-T18이 탈퇴 때 댓글을 사유 WITHDRAWAL로 지우게 되면 이 분기를 지운다
            !hasProfile -> SnsCommentItemStatus.WITHDRAWN

            else -> SnsCommentItemStatus.ACTIVE
        }

    /** 안내 문구 항목은 작성자와 내용이 null이고 수는 0, 불리언은 false다 */
    private fun item(
        publicId: String,
        parentPublicId: String?,
        status: SnsCommentItemStatus,
        rootStatus: SnsCommentItemStatus?,
        author: SnsPostAuthorResult?,
        content: String?,
        edited: Boolean,
        isMine: Boolean,
        createdAt: LocalDateTime,
    ): SnsCommentItemResult {
        val active = status == SnsCommentItemStatus.ACTIVE
        return SnsCommentItemResult(
            publicId = publicId,
            parentPublicId = parentPublicId,
            status = status,
            author = author.takeIf { active },
            content = content.takeIf { active },
            likeCount = 0,
            liked = false,
            edited = active && edited,
            replyable = active && (rootStatus ?: status) == SnsCommentItemStatus.ACTIVE,
            isMine = active && isMine,
            createdAt = createdAt,
        )
    }
}
