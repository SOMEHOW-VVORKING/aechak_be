package com.aechak.application.sns.facade

import com.aechak.application.sns.service.CommentQueryService
import com.aechak.application.sns.service.PostAccessService
import com.aechak.application.sns.support.CommentCursorCodec
import com.aechak.application.sns.usecase.SnsCommentUseCase
import com.aechak.application.sns.usecase.query.SnsCommentListQuery
import com.aechak.application.sns.usecase.result.SnsCommentItemResult
import com.aechak.application.support.CursorPageResult
import com.aechak.application.user.user.usecase.UserUseCase
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/** SNS 댓글 진입점. 금지어 판정은 댓글 쓰기만 쓰므로 좋아요와 나눠 둔다 */
@Service
class SnsCommentFacade(
    private val commentQueryService: CommentQueryService,
    private val postAccessService: PostAccessService,
    private val userUseCase: UserUseCase,
) : SnsCommentUseCase {
    @Transactional(readOnly = true)
    override fun getComments(query: SnsCommentListQuery): CursorPageResult<SnsCommentItemResult> {
        val post = postAccessService.requirePublished(query.postPublicId)
        postAccessService.requireActiveAuthor(userUseCase.findPublicProfileById(post.authorId))
        postAccessService.requireNotBlocked(post.authorId, query.viewerId)
        val lastRootId = query.cursor?.let { CommentCursorCodec.decode(it, post.publicId) }
        return commentQueryService.getPage(commentQueryService.scopeOf(post, query.viewerId), post.publicId, lastRootId, query.size)
    }
}
