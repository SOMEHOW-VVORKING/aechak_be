package com.aechak.application.product.search.port

import com.aechak.application.product.product.port.view.ProductCatalogView
import java.time.LocalDateTime

interface ProductKeywordSearchPort {
    fun search(condition: ProductKeywordSearchCondition): List<ProductCatalogView>

    /** 목록과 동일한 필터를 적용한 총개수 */
    fun countMatching(
        filter: ProductKeywordFilter,
        now: LocalDateTime,
    ): Long

    /** 커서 keyset 해석용 내부 id 변환 */
    fun findIdByPublicId(publicId: String): Long?
}
