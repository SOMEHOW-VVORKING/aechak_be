package com.aechak.application.product.search.port

import com.aechak.application.product.product.port.view.ProductCatalogView

interface ProductKeywordSearchPort {
    /** 검색이 DB를 읽는지. false면 Facade가 검색 호출을 트랜잭션 밖에서 함 */
    val readsDatabase: Boolean

    fun search(condition: ProductKeywordSearchCondition): List<ProductCatalogView>

    /** 커서 없는 첫 페이지 목록과 같은 필터를 적용한 총개수를 한 번에 조회 */
    fun searchFirstPage(condition: ProductKeywordSearchCondition): ProductKeywordSearchFirstPage

    /** 커서 keyset 해석용 내부 id 변환 */
    fun findIdByPublicId(publicId: String): Long?
}
