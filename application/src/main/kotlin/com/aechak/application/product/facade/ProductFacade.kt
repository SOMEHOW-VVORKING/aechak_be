package com.aechak.application.product.facade

import com.aechak.application.product.service.ProductService
import com.aechak.application.product.stats.service.ProductStatsService
import com.aechak.application.product.usecase.ProductUseCase
import com.aechak.application.product.usecase.command.RegisterProductCommand
import com.aechak.application.product.usecase.query.ProductSearchQuery
import com.aechak.application.product.usecase.result.ProductOptionsResult
import com.aechak.application.product.usecase.result.ProductRegisterResult
import com.aechak.application.product.usecase.result.ProductResult
import com.aechak.application.product.usecase.result.ProductSummaryResult
import com.aechak.application.seller.usecase.SellerUseCase
import com.aechak.application.support.CursorPageResult
import com.aechak.common.error.BusinessException
import com.aechak.domain.product.error.ProductErrorCode
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime

@Service
class ProductFacade(
    private val productService: ProductService,
    private val productStatsService: ProductStatsService,
    private val sellerUseCase: SellerUseCase,
) : ProductUseCase {
    @Transactional(readOnly = true)
    override fun getProducts(query: ProductSearchQuery): CursorPageResult<ProductSummaryResult> {
        val now = LocalDateTime.now()
        val page = productService.getVisiblePage(query, now)
        val statsById = productStatsService.getStatsByProductIds(page.items.map { it.id })
        return CursorPageResult(
            items =
                page.items.map {
                    ProductSummaryResult.from(view = it, stats = statsById[it.id], now = now)
                },
            totalCount = page.totalCount,
            nextCursor = page.nextCursor,
            hasNext = page.hasNext,
        )
    }

    @Transactional(readOnly = true)
    override fun getProduct(
        publicId: String,
        userId: Long?,
    ): ProductResult {
        val now = LocalDateTime.now()
        val detail = productService.getVisibleDetail(publicId)
        val images = productService.getImages(detail.id)
        val stats = productStatsService.getStatsByProductIds(listOf(detail.id))[detail.id]
        val isLiked = userId?.let { productService.isLiked(detail.id, it) } ?: false
        return ProductResult.from(view = detail, images = images, stats = stats, isLiked = isLiked, now = now)
    }

    @Transactional(readOnly = true)
    override fun getProductOptions(publicId: String): ProductOptionsResult =
        ProductOptionsResult.from(productService.getVisibleOptions(publicId))

    @Transactional
    override fun registerProduct(command: RegisterProductCommand): ProductRegisterResult {
        if (!sellerUseCase.isActiveSeller(command.sellerId)) {
            throw BusinessException(ProductErrorCode.PRODUCT_SELLER_NOT_ACTIVE)
        }
        val version = productService.register(command)
        return ProductRegisterResult.of(version.product, version.versionNo)
    }
}
