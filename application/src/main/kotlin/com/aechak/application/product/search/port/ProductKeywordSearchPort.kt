package com.aechak.application.product.search.port

import com.aechak.application.product.product.port.view.ProductCatalogView

interface ProductKeywordSearchPort {
    fun search(condition: ProductKeywordSearchCondition): List<ProductCatalogView>

    /** 커서 없는 첫 페이지 목록과 같은 필터를 적용한 총개수를 한 번에 조회 */
    fun searchFirstPage(condition: ProductKeywordSearchCondition): ProductKeywordSearchFirstPage

    /** 커서 keyset 해석용 내부 id 변환 */
    fun findIdByPublicId(publicId: String): Long?
}
