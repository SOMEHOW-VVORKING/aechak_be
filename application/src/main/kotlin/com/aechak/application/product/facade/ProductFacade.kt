package com.aechak.application.product.facade

import com.aechak.application.product.service.ProductService
import com.aechak.application.product.usecase.ProductUseCase
import com.aechak.application.product.usecase.query.ProductSearchQuery
import com.aechak.application.product.usecase.result.ProductSummaryResult
import com.aechak.application.support.CursorPageResult
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime

@Service
class ProductFacade(
    private val productService: ProductService,
) : ProductUseCase {
    @Transactional(readOnly = true)
    override fun getProducts(query: ProductSearchQuery): CursorPageResult<ProductSummaryResult> {
        val now = LocalDateTime.now()
        val page = productService.getVisiblePage(query, now)
        val statsById = productService.getStatsByProductIds(page.items.map { it.id })
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
}
