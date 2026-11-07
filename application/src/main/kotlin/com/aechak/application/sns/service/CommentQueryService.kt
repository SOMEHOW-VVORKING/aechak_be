package com.aechak.application.sns.service

import com.aechak.application.file.usecase.FileUseCase
import com.aechak.application.sns.port.SnsCommentQueryPort
import com.aechak.application.sns.port.SnsCommentScope
import com.aechak.application.sns.port.view.SnsCommentRefView
import com.aechak.application.sns.port.view.SnsCommentView
import com.aechak.application.sns.support.CommentCursorCodec
import com.aechak.application.sns.usecase.result.SnsCommentItemResult
import com.aechak.application.sns.usecase.result.SnsCommentItemStatus
import com.aechak.application.sns.usecase.result.SnsCommentThreadResult
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
import com.aechak.domain.user.user.enums.UserStatus
import org.springframework.stereotype.Service
import java.time.LocalDateTime

/** 댓글 읽기. 보는 사람 기준 거르기는 포트의 조건식이 하고 이 서비스는 항목을 조립한다 */
@Service
class CommentQueryService(
    private val commentQueryPort: SnsCommentQueryPort,
    private val snsVisibilityService: SnsVisibilityService,
    private val fileUseCase: FileUseCase,
    private val userUseCase: UserUseCase,
    private val likeService: LikeService,
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

    /** 댓글과 그 최상위 댓글을 목록과 같은 규칙으로 판정하고 조립한다. 보이지 않으면 130700이다 */
    fun getThread(
        scope: SnsCommentScope,
        comment: Comment,
    ): SnsCommentThreadResult {
        val view = commentQueryPort.findVisible(scope, comment.id) ?: throw BusinessException(SnsErrorCode.SNS_COMMENT_UNAVAILABLE)
        val rootView =
            view.parentId?.let { commentQueryPort.findVisible(scope, it) ?: throw BusinessException(SnsErrorCode.SNS_COMMENT_UNAVAILABLE) }
        val likes = likesOf(listOfNotNull(view.id, rootView?.id), scope.viewerId)
        val parent = rootView?.let { toItem(it, null, null, scope.viewerId, likes) }
        return SnsCommentThreadResult(toItem(view, rootView?.publicId, parent?.status, scope.viewerId, likes), parent)
    }

    /** 상세와 피드 카드의 댓글 수. 섀도우 처리된 사용자에게만 자기 숨긴 댓글과 그 아래 대댓글이 더해진다 */
    fun countVisible(
        postIds: Collection<Long>,
        viewerId: Long?,
    ): Map<Long, Long> = commentQueryPort.countVisible(postIds, viewerId)

    /**
     * 보는 사람에게 보이는 이 게시물의 댓글을 답글 대상으로 찾는다. 없거나 보이지 않으면 130700이다.
     * 포트가 사용자 표를 읽지 않으므로 영구 정지 작성자의 대댓글은 여기서 거르고 최상위 댓글은 [requireOpenRoot]가 거른다
     */
    fun requireReplyTarget(
        scope: SnsCommentScope,
        commentPublicId: String,
    ): SnsCommentRefView {
        val ref = findVisibleRef(scope, commentPublicId)
        if (ref.parentId != null && isBanned(ref.authorId)) throw BusinessException(SnsErrorCode.SNS_COMMENT_UNAVAILABLE)
        return ref
    }

    /** 좋아요할 수 있는 댓글의 id. 보이지 않거나 안내 문구만 남은 댓글(지운 댓글, 영구 정지 작성자의 댓글)이면 130700이다 */
    fun requireLikeTarget(
        scope: SnsCommentScope,
        commentPublicId: String,
    ): Long {
        val ref = findVisibleRef(scope, commentPublicId)
        if (ref.status == CommentStatus.DELETED || isBanned(ref.authorId)) throw BusinessException(SnsErrorCode.SNS_COMMENT_UNAVAILABLE)
        return ref.id
    }

    /** 영구 정지 작성자의 최상위 댓글은 안내 문구라 지운 댓글처럼 답글을 받지 않는다(130705) */
    fun requireOpenRoot(root: Comment) {
        if (isBanned(root.authorId)) throw BusinessException(SnsErrorCode.SNS_COMMENT_REPLY_CLOSED)
    }

    private fun findVisibleRef(
        scope: SnsCommentScope,
        commentPublicId: String,
    ): SnsCommentRefView =
        commentQueryPort.findVisibleRef(scope, commentPublicId) ?: throw BusinessException(SnsErrorCode.SNS_COMMENT_UNAVAILABLE)

    private fun isBanned(userId: Long): Boolean = userUseCase.findPublicProfileById(userId)?.status == UserStatus.BANNED

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
        val replyViews = commentQueryPort.findReplies(scope, roots.items.map { it.id })
        val likes = likesOf(roots.items.map { it.id } + replyViews.map { it.id }, scope.viewerId)
        val replies = replyViews.groupBy { it.parentId }
        val items =
            roots.items.flatMap { root ->
                val rootItem = toItem(root, null, null, scope.viewerId, likes)
                listOf(rootItem) + replies[root.id].orEmpty().map { toItem(it, root.publicId, rootItem.status, scope.viewerId, likes) }
            }
        return CursorPageResult(items, null, roots.nextCursor, roots.hasNext)
    }

    /** rootStatus는 대댓글이면 최상위 댓글 항목의 상태이고 최상위 댓글이면 null이다 */
    private fun toItem(
        view: SnsCommentView,
        parentPublicId: String?,
        rootStatus: SnsCommentItemStatus?,
        viewerId: Long?,
        likes: CommentLikes,
    ): SnsCommentItemResult {
        val author =
            view.authorNickname?.takeIf { view.authorStatus != UserStatus.BANNED }?.let {
                SnsPostAuthorResult(
                    view.authorPublicId!!,
                    it,
                    fileUseCase.resolveMediaUrl(view.authorProfileImageKey),
                )
            }
        return item(
            publicId = view.publicId,
            parentPublicId = parentPublicId,
            status = statusOf(view.status, view.deletionReason, view.authorStatus == UserStatus.BANNED),
            rootStatus = rootStatus,
            author = author,
            content = view.content,
            edited = view.editedAt != null,
            isMine = view.authorId == viewerId,
            createdAt = view.createdAt,
            likeCount = likes.counts[view.id] ?: 0,
            liked = view.id in likes.liked,
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
        val likes = likesOf(listOf(comment.id), viewerId)
        return item(
            publicId = comment.publicId,
            parentPublicId = root?.publicId,
            status = statusOf(comment.status, comment.deletionReason, authorBanned = false),
            rootStatus = root?.let { statusOf(it.status, it.deletionReason, isBanned(it.authorId)) },
            author = author,
            content = comment.content,
            edited = comment.editedAt != null,
            isMine = comment.authorId == viewerId,
            createdAt = comment.createdAt,
            likeCount = likes.counts[comment.id] ?: 0,
            liked = comment.id in likes.liked,
        )
    }

    /** 항목 댓글 id로 좋아요 수와 내 좋아요 여부를 한 번씩 읽는다 */
    private fun likesOf(
        commentIds: List<Long>,
        viewerId: Long?,
    ): CommentLikes = CommentLikes(likeService.countByCommentIds(commentIds), likeService.findLikedCommentIdsAmong(viewerId, commentIds))

    /** 프로필 행이 없는 작성자(탈퇴)와 영구 정지 작성자는 null이다 */
    private fun authorOf(userId: Long): SnsPostAuthorResult? =
        userUseCase.findPublicProfileById(userId)?.takeIf { it.status != UserStatus.BANNED }?.let { profile ->
            profile.nickname?.let { SnsPostAuthorResult(profile.publicId, it, profile.profileImageUrl) }
        }

    /** authorBanned면 영구 정지 작성자다 */
    private fun statusOf(
        status: CommentStatus,
        reason: CommentDeletionReason?,
        authorBanned: Boolean,
    ): SnsCommentItemStatus =
        when {
            status == CommentStatus.DELETED && reason == CommentDeletionReason.WITHDRAWAL -> SnsCommentItemStatus.WITHDRAWN

            status == CommentStatus.DELETED -> SnsCommentItemStatus.DELETED

            // 영구 정지 작성자의 댓글은 탈퇴한 사용자의 댓글과 같은 안내 문구다. 목록 조건이 대댓글이 남은 최상위 댓글만 남긴다
            authorBanned -> SnsCommentItemStatus.WITHDRAWN

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
        likeCount: Long,
        liked: Boolean,
    ): SnsCommentItemResult {
        val active = status == SnsCommentItemStatus.ACTIVE
        return SnsCommentItemResult(
            publicId = publicId,
            parentPublicId = parentPublicId,
            status = status,
            author = author.takeIf { active },
            content = content.takeIf { active },
            likeCount = if (active) likeCount else 0,
            liked = active && liked,
            edited = active && edited,
            replyable = active && (rootStatus ?: status) == SnsCommentItemStatus.ACTIVE,
            isMine = active && isMine,
            createdAt = createdAt,
        )
    }
}

/** 항목 댓글들의 좋아요 수와 보는 사람이 좋아요한 댓글 id */
private class CommentLikes(
    val counts: Map<Long, Long>,
    val liked: Set<Long>,
)
