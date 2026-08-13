package com.aechak.domain.product.like.repository

/** 유저-상품 찜 저장소 포트 */
interface ProductLikeRepository {
    fun existsByProductIdAndUserId(
        productId: Long,
        userId: Long,
    ): Boolean

    /** 찜 원자 삽입 */
    fun insertIfAbsent(
        productId: Long,
        userId: Long,
    ): Boolean

    /** 찜 하드 삭제, 삭제된 행 수 반환 */
    fun deleteByProductIdAndUserId(
        productId: Long,
        userId: Long,
    ): Int
}
