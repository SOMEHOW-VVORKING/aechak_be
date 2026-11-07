package com.aechak.domain.sns.comment.repository

import com.aechak.domain.sns.comment.Comment

interface CommentRepository {
    fun save(comment: Comment): Comment

    fun findByPublicId(publicId: String): Comment?

    /** 쓰기 경로 전용. 행을 잠가 읽는다 */
    fun findByPublicIdForUpdate(publicId: String): Comment?

    /** 쓰기 경로 전용. 행을 잠가 읽는다 */
    fun findByIdForUpdate(id: Long): Comment?

    fun findByIdempotencyKey(
        authorId: Long,
        postId: Long,
        idempotencyKey: String,
    ): Comment?

    /** 쓰기 경로 전용. 작성자의 지우지 않은 댓글을 모두 잠가 읽는다 */
    fun findActiveByAuthorIdForUpdate(authorId: Long): List<Comment>
}
