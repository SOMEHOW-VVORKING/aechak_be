package com.aechak.application.sns.service

import com.aechak.common.error.BusinessException
import com.aechak.domain.sns.comment.Comment
import com.aechak.domain.sns.comment.enums.CommentStatus
import com.aechak.domain.sns.comment.repository.CommentRepository
import com.aechak.domain.sns.error.SnsErrorCode
import com.aechak.domain.sns.post.Post
import com.aechak.domain.sns.post.repository.PostRepository
import org.springframework.stereotype.Service

/** 댓글 쓰기. 판정은 Facade가 끝낸 뒤 부른다 */
@Service
class CommentCommandService(
    private val commentRepository: CommentRepository,
    private val postRepository: PostRepository,
) {
    fun write(
        post: Post,
        root: Comment?,
        authorId: Long,
        content: String,
        idempotencyKey: String,
    ): Comment = commentRepository.save(Comment.write(post, root, authorId, content, idempotencyKey))

    /**
     * 답글이 붙을 최상위 댓글을 잠가 읽는다. 지운 댓글이면 130705다.
     * 이 트랜잭션에서 그 엔티티를 처음 읽는 자리여야 한다. 먼저 읽어 두면 잠금을 기다리는 사이 커밋된 삭제를 놓친다
     */
    fun lockReplyRoot(rootId: Long): Comment {
        val root = commentRepository.findByIdForUpdate(rootId) ?: throw BusinessException(SnsErrorCode.SNS_COMMENT_UNAVAILABLE)
        if (root.status == CommentStatus.DELETED) throw BusinessException(SnsErrorCode.SNS_COMMENT_REPLY_CLOSED)
        return root
    }

    /** 같은 작성자가 이 게시물에 같은 키로 쓴 댓글. 게시물 상태는 보지 않는다 */
    fun findByIdempotencyKey(
        authorId: Long,
        postPublicId: String,
        idempotencyKey: String,
    ): Comment? =
        postRepository.findByPublicId(postPublicId)?.let { commentRepository.findByIdempotencyKey(authorId, it.id, idempotencyKey) }
}
