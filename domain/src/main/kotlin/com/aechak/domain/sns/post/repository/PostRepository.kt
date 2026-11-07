package com.aechak.domain.sns.post.repository

import com.aechak.domain.sns.post.Post

interface PostRepository {
    fun save(post: Post): Post

    fun findByPublicId(publicId: String): Post?

    /** 쓰기 경로 전용 — 행을 잠가 읽는다. */
    fun findByPublicIdForUpdate(publicId: String): Post?

    fun findByAuthorIdAndIdempotencyKey(
        authorId: Long,
        idempotencyKey: String,
    ): Post?

    /** 쓰기 경로 전용. 작성자의 게시된 게시물을 모두 잠가 읽는다 */
    fun findPublishedByAuthorIdForUpdate(authorId: Long): List<Post>
}
