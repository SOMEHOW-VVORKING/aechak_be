package com.aechak.application.sns.service

import com.aechak.domain.sns.comment.Comment
import com.aechak.domain.sns.comment.repository.CommentRepository
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

    /** 같은 작성자가 이 게시물에 같은 키로 쓴 댓글. 게시물 상태는 보지 않는다 */
    fun findByIdempotencyKey(
        authorId: Long,
        postPublicId: String,
        idempotencyKey: String,
    ): Comment? =
        postRepository.findByPublicId(postPublicId)?.let { commentRepository.findByIdempotencyKey(authorId, it.id, idempotencyKey) }
}
