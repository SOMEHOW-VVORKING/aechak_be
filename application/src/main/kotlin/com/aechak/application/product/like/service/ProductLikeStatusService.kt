package com.aechak.application.product.like.service

import com.aechak.domain.product.like.repository.ProductLikeRepository
import org.springframework.stereotype.Service

/** 목록·검색 카드의 찜 여부 배치 판정 */
@Service
class ProductLikeStatusService(
    private val productLikeRepository: ProductLikeRepository,
) {
    fun likedProductIds(
        userId: Long?,
        productIds: List<Long>,
    ): Set<Long> =
        if (userId == null || productIds.isEmpty()) {
            emptySet()
        } else {
            productLikeRepository.findLikedProductIds(userId, productIds)
        }
}
