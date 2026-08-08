package com.aechak.application.product.like.port

import java.time.LocalDateTime

/** 내 찜 목록 조회 조건 */
data class LikedProductCondition(
    val userId: Long,
    val lastLikeId: Long?,
    val limit: Int,
    val now: LocalDateTime,
) {
    init {
        require(limit > 0) { "limit은 양수여야 합니다." }
    }
}
