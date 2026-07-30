package com.aechak.domain.product.like.repository

/** "유저-상품 1건" 멱등은 UNIQUE + 어댑터 upsert/삭제. */
interface ProductLikeRepository {
    fun existsByProductIdAndUserId(
        productId: Long,
        userId: Long,
    ): Boolean
}
