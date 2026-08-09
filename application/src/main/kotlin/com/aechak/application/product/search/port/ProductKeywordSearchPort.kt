package com.aechak.application.product.search.port

import com.aechak.application.product.port.view.ProductCatalogView

interface ProductKeywordSearchPort {
    fun search(condition: ProductKeywordSearchCondition): List<ProductCatalogView>

    fun countMatching(keyword: String): Long

    /** 커서 keyset 해석용 내부 id 변환 */
    fun findIdByPublicId(publicId: String): Long?
}
