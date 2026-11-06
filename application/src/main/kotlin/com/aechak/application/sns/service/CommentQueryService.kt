package com.aechak.application.sns.service

import com.aechak.application.file.usecase.FileUseCase
import com.aechak.application.sns.port.SnsCommentQueryPort
import com.aechak.application.sns.port.SnsCommentScope
import com.aechak.application.sns.port.view.SnsCommentView
import com.aechak.application.sns.support.CommentCursorCodec
import com.aechak.application.sns.usecase.result.SnsCommentItemResult
import com.aechak.application.sns.usecase.result.SnsCommentItemStatus
import com.aechak.application.sns.usecase.result.SnsPostAuthorResult
import com.aechak.application.support.CursorPageResult
import com.aechak.application.support.CursorPageSize
import com.aechak.domain.sns.comment.enums.CommentDeletionReason
import com.aechak.domain.sns.comment.enums.CommentStatus
import com.aechak.domain.sns.post.Post
import org.springframework.stereotype.Service

/** 댓글 읽기. 보는 사람 기준 거르기는 포트의 조건식이 하고 이 서비스는 항목을 조립한다 */
@Service
class CommentQueryService(
    private val commentQueryPort: SnsCommentQueryPort,
    private val snsVisibilityService: SnsVisibilityService,
    private val fileUseCase: FileUseCase,
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
        val status =
            when {
                view.status == CommentStatus.DELETED && view.deletionReason == CommentDeletionReason.WITHDRAWAL -> {
                    SnsCommentItemStatus.WITHDRAWN
                }

                view.status == CommentStatus.DELETED -> {
                    SnsCommentItemStatus.DELETED
                }

                // shortcut: SNS-T18 전에 탈퇴한 작성자의 댓글은 프로필 행이 없어 안내 문구로 바꿔 보여주고 수에는 남긴다.
                // SNS-T18이 탈퇴 때 댓글을 사유 WITHDRAWAL로 지우게 되면 이 분기를 지운다
                view.authorNickname == null -> {
                    SnsCommentItemStatus.WITHDRAWN
                }

                else -> {
                    SnsCommentItemStatus.ACTIVE
                }
            }
        val active = status == SnsCommentItemStatus.ACTIVE
        return SnsCommentItemResult(
            publicId = view.publicId,
            parentPublicId = parentPublicId,
            status = status,
            author =
                if (active) {
                    SnsPostAuthorResult(
                        view.authorPublicId!!,
                        view.authorNickname!!,
                        fileUseCase.resolveMediaUrl(view.authorProfileImageKey),
                    )
                } else {
                    null
                },
            content = view.content.takeIf { active },
            likeCount = 0,
            liked = false,
            edited = active && view.editedAt != null,
            replyable = active && (rootStatus ?: status) == SnsCommentItemStatus.ACTIVE,
            isMine = active && view.authorId == viewerId,
            createdAt = view.createdAt,
        )
    }
}
