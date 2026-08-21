package com.aechak.application.product.like.facade

import com.aechak.application.product.like.service.ProductLikeService
import com.aechak.application.product.like.usecase.ProductLikeUseCase
import com.aechak.application.product.like.usecase.command.ProductLikeCommand
import com.aechak.application.product.like.usecase.query.LikedProductListQuery
import com.aechak.application.product.product.usecase.result.ProductSummaryResult
import com.aechak.application.support.CursorPageResult
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime

@Service
class ProductLikeFacade(
    private val productLikeService: ProductLikeService,
) : ProductLikeUseCase {
    @Transactional
    override fun like(command: ProductLikeCommand) = productLikeService.like(command)

    @Transactional
    override fun unlike(command: ProductLikeCommand) = productLikeService.unlike(command)

    @Transactional(readOnly = true)
    override fun getLikedProducts(
        query: LikedProductListQuery,
        userId: Long,
    ): CursorPageResult<ProductSummaryResult> {
        val now = LocalDateTime.now()
        val page = productLikeService.getLikedPage(query, userId, now)
        return CursorPageResult(
            items =
                page.items.map {
                    ProductSummaryResult.from(view = it.product, now = now, isLiked = true)
                },
            totalCount = page.totalCount,
            nextCursor = page.nextCursor,
            hasNext = page.hasNext,
        )
    }
}
