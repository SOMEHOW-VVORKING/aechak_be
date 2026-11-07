package com.aechak.application.product.search.service

import com.aechak.application.product.search.port.ProductSearchDocumentReader
import com.aechak.application.product.search.port.ProductSearchIndexWriter
import org.slf4j.LoggerFactory

/**
 * 검색 색인을 상품 전체로 다시 만든다
 * 색인 쓰기 어댑터가 api에만 있어 빈 등록은 api 조립이 맡는다
 */
class ProductSearchReindexService(
    private val reader: ProductSearchDocumentReader,
    private val writer: ProductSearchIndexWriter,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    /** 색인한 상품 수를 반환 */
    fun reindexAll(): Long {
        val startedAt = System.currentTimeMillis()
        writer.recreate()
        var count = 0L
        var lastId = 0L
        do {
            val batch = reader.readAfter(lastId, BATCH_SIZE)
            if (batch.isEmpty()) break
            writer.index(batch)
            count += batch.size
            lastId = batch.last().id
        } while (batch.size == BATCH_SIZE)
        writer.finish()
        log.info("SEARCH_REINDEX_DONE count={} ms={}", count, System.currentTimeMillis() - startedAt)
        return count
    }

    companion object {
        private const val BATCH_SIZE = 1_000
    }
}
