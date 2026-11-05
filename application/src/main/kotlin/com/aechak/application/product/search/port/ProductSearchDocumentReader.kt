package com.aechak.application.product.search.port

/** 검색 색인 원본 읽기 */
fun interface ProductSearchDocumentReader {
    /** id가 afterId보다 큰 상품을 id 오름차순으로 최대 size개 */
    fun readAfter(
        afterId: Long,
        size: Int,
    ): List<ProductSearchDocument>
}
