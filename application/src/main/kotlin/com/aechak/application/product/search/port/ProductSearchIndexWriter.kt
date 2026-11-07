package com.aechak.application.product.search.port

/** 검색 색인 전체 재작성 */
interface ProductSearchIndexWriter {
    /** 색인을 지우고 빈 색인을 새로 만든다 */
    fun recreate()

    fun index(documents: List<ProductSearchDocument>)

    /** 적재가 끝난 색인을 검색에 보이게 한다 */
    fun finish()
}
