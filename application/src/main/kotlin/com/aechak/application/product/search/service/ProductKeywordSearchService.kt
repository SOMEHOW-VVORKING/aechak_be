package com.aechak.application.product.search.service

import com.aechak.application.product.product.port.view.ProductCatalogView
import com.aechak.application.product.search.port.ProductKeywordFilter
import com.aechak.application.product.search.port.ProductKeywordSearchCondition
import com.aechak.application.product.search.port.ProductKeywordSearchPort
import com.aechak.application.product.search.port.ProductKeywordSearchSort
import com.aechak.application.product.search.support.ProductKeywordSearchCursorCodec
import com.aechak.application.product.search.usecase.query.ProductKeywordSearchQuery
import com.aechak.application.support.CursorPageResult
import com.aechak.common.error.BusinessException
import com.aechak.common.error.CommonErrorCode
import com.aechak.domain.product.category.Category
import com.aechak.domain.product.category.repository.CategoryRepository
import com.aechak.domain.product.error.ProductErrorCode
import org.springframework.stereotype.Service
import java.text.Normalizer
import java.time.LocalDateTime

@Service
class ProductKeywordSearchService(
    private val productKeywordSearchPort: ProductKeywordSearchPort,
    private val categoryRepository: CategoryRepository,
) {
    fun searchPage(
        query: ProductKeywordSearchQuery,
        now: LocalDateTime,
    ): CursorPageResult<ProductCatalogView> {
        val filter = toFilter(query)
        validateCategoryFilter(filter.categoryId)
        val anchor = query.cursor?.let { resolveCursor(it, query.sort, filter, now) }
        val queryNow = anchor?.anchorNow ?: now
        val fetched =
            productKeywordSearchPort.search(
                ProductKeywordSearchCondition(
                    filter = filter,
                    sort = query.sort,
                    lastId = anchor?.lastId,
                    lastReviewCount = anchor?.lastReviewCount,
                    lastPrice = anchor?.lastPrice,
                    limit = query.size + 1,
                    now = queryNow,
                ),
            )
        val hasNext = fetched.size > query.size
        val page = if (hasNext) fetched.take(query.size) else fetched
        return CursorPageResult(
            items = page,
            // 첫 페이지에서만 총개수 계산
            totalCount = if (query.cursor == null) productKeywordSearchPort.countMatching(filter, queryNow) else null,
            nextCursor = if (hasNext) encodeCursor(query.sort, filter, page.last(), queryNow) else null,
            hasNext = hasNext,
        )
    }

    private fun toFilter(query: ProductKeywordSearchQuery): ProductKeywordFilter =
        ProductKeywordFilter(
            keyword = normalize(query.keyword),
            minPrice = query.minPrice,
            maxPrice = query.maxPrice,
            minRating = query.minRating,
            categoryId = query.categoryId,
            freeShipping = query.freeShipping,
            excludeSoldOut = query.excludeSoldOut,
        )

    /** DB 검색과 커서 비교 기준을 맞추기 위한 검색어 정규화(대소문자 무시 매칭용 lowercase 포함) */
    private fun normalize(keyword: String): String = Normalizer.normalize(keyword.trim(), Normalizer.Form.NFC).lowercase()

    /** 카테고리 필터는 중분류(depth 2)까지만 허용 */
    private fun validateCategoryFilter(categoryId: Long?) {
        if (categoryId == null) return
        val category =
            categoryRepository.findActiveById(categoryId)
                ?: throw BusinessException(ProductErrorCode.CATEGORY_NOT_FOUND)
        if (category.depth != Category.MID_DEPTH) {
            throw BusinessException(ProductErrorCode.INVALID_CATEGORY_FILTER)
        }
    }

    private fun encodeCursor(
        sort: ProductKeywordSearchSort,
        filter: ProductKeywordFilter,
        last: ProductCatalogView,
        now: LocalDateTime,
    ): String =
        ProductKeywordSearchCursorCodec.encode(
            sort = sort,
            filterHash = ProductKeywordSearchCursorCodec.filterHash(filter),
            publicId = last.publicId,
            lastReviewCount = last.popularityScore,
            lastPrice = last.sortPriceAtAnchor,
            now = now,
        )

    private fun resolveCursor(
        raw: String,
        sort: ProductKeywordSearchSort,
        filter: ProductKeywordFilter,
        now: LocalDateTime,
    ): CursorAnchor {
        val decoded = ProductKeywordSearchCursorCodec.decode(raw, sort)
        if (decoded.filterHash != ProductKeywordSearchCursorCodec.filterHash(filter)) {
            throw BusinessException(CommonErrorCode.INVALID_CURSOR)
        }
        // 미래 시각 커서만 거절
        if (decoded.anchorNow.isAfter(now)) {
            throw BusinessException(CommonErrorCode.INVALID_CURSOR)
        }
        val lastId =
            productKeywordSearchPort.findIdByPublicId(decoded.publicId)
                ?: throw BusinessException(CommonErrorCode.INVALID_CURSOR)
        return CursorAnchor(
            lastId = lastId,
            lastReviewCount = decoded.lastReviewCount,
            lastPrice = decoded.lastPrice,
            anchorNow = decoded.anchorNow,
        )
    }

    private data class CursorAnchor(
        val lastId: Long,
        val lastReviewCount: Int?,
        val lastPrice: Long?,
        val anchorNow: LocalDateTime,
    )
}
