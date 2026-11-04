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
}
