package com.aechak.application.product.search.service

import com.aechak.application.product.port.view.ProductCatalogView
import com.aechak.application.product.search.port.ProductKeywordSearchCondition
import com.aechak.application.product.search.port.ProductKeywordSearchPort
import com.aechak.application.product.search.support.ProductKeywordSearchCursorCodec
import com.aechak.application.product.search.usecase.query.ProductKeywordSearchQuery
import com.aechak.application.support.CursorPageResult
import com.aechak.common.error.BusinessException
import com.aechak.common.error.CommonErrorCode
import org.springframework.stereotype.Service
import java.text.Normalizer
import java.time.LocalDateTime

@Service
class ProductKeywordSearchService(
    private val productKeywordSearchPort: ProductKeywordSearchPort,
) {
    fun searchPage(
        query: ProductKeywordSearchQuery,
        now: LocalDateTime,
    ): CursorPageResult<ProductCatalogView> {
        val keyword = normalize(query.keyword)
        val lastId = query.cursor?.let { resolveCursor(it, keyword) }
        val fetched =
            productKeywordSearchPort.search(
                ProductKeywordSearchCondition(
                    keyword = keyword,
                    lastId = lastId,
                    limit = query.size + 1,
                    now = now,
                ),
            )
        val hasNext = fetched.size > query.size
        val page = if (hasNext) fetched.take(query.size) else fetched
        return CursorPageResult(
            items = page,
            totalCount = if (query.cursor == null) productKeywordSearchPort.countMatching(keyword) else null,
            nextCursor = if (hasNext) ProductKeywordSearchCursorCodec.encode(keyword, page.last().publicId) else null,
            hasNext = hasNext,
        )
    }

    /** DB 검색과 커서 비교 기준을 맞추기 위한 검색어 정규화 */
    private fun normalize(keyword: String): String = Normalizer.normalize(keyword.trim(), Normalizer.Form.NFC).lowercase()

    private fun resolveCursor(
        raw: String,
        keyword: String,
    ): Long {
        val decoded = ProductKeywordSearchCursorCodec.decode(raw)
        if (decoded.keyword != keyword) {
            throw BusinessException(CommonErrorCode.INVALID_CURSOR)
        }
        return productKeywordSearchPort.findIdByPublicId(decoded.publicId)
            ?: throw BusinessException(CommonErrorCode.INVALID_CURSOR)
    }
}
