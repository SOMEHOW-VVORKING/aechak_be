package com.aechak.application.product.like.service

import com.aechak.application.product.like.port.LikedProductCondition
import com.aechak.application.product.like.port.LikedProductQueryPort
import com.aechak.application.product.like.port.view.LikedProductView
import com.aechak.application.product.like.support.LikedProductCursorCodec
import com.aechak.application.product.like.usecase.command.ProductLikeCommand
import com.aechak.application.product.like.usecase.query.LikedProductListQuery
import com.aechak.application.support.CursorPageResult
import com.aechak.application.support.CursorPageSize
import com.aechak.common.error.BusinessException
import com.aechak.domain.product.error.ProductErrorCode
import com.aechak.domain.product.like.repository.ProductLikeRepository
import com.aechak.domain.product.product.repository.ProductRepository
import com.aechak.domain.product.stats.repository.ProductStatsRepository
import org.springframework.stereotype.Service
import java.time.LocalDateTime

@Service
class ProductLikeService(
    private val productRepository: ProductRepository,
    private val productLikeRepository: ProductLikeRepository,
    private val productStatsRepository: ProductStatsRepository,
    private val likedProductQueryPort: LikedProductQueryPort,
) {
    fun like(command: ProductLikeCommand) {
        val productId = resolveProductId(command.productPublicId)
        if (productLikeRepository.insertIfAbsent(productId, command.userId)) {
            productStatsRepository.increaseLikeCount(productId)
        }
    }

    fun unlike(command: ProductLikeCommand) {
        val productId = resolveProductId(command.productPublicId)
        if (productLikeRepository.deleteByProductIdAndUserId(productId, command.userId) > 0) {
            productStatsRepository.decreaseLikeCount(productId)
        }
    }

    fun getLikedPage(
        query: LikedProductListQuery,
        userId: Long,
        now: LocalDateTime,
    ): CursorPageResult<LikedProductView> {
        val lastLikeId = query.cursor?.let { LikedProductCursorCodec.decode(it) }
        val fetched =
            likedProductQueryPort.findLikedPage(
                LikedProductCondition(
                    userId = userId,
                    lastLikeId = lastLikeId,
                    limit = CursorPageSize.fetchLimit(query.size),
                    now = now,
                ),
            )
        return CursorPageResult.of(
            fetched = fetched,
            size = query.size,
            // 첫 페이지에서만 총개수 계산
            totalCount = if (query.cursor == null) likedProductQueryPort.countLiked(userId) else null,
        ) { last -> LikedProductCursorCodec.encode(last.likeId) }
    }

    private fun resolveProductId(publicId: String): Long =
        productRepository.findIdByPublicId(publicId)
            ?: throw BusinessException(ProductErrorCode.PRODUCT_NOT_FOUND)
}
