package com.aechak.application.product.search.service

import com.aechak.application.product.search.port.ProductSearchDocument
import com.aechak.application.product.search.port.ProductSearchDocumentReader
import com.aechak.application.product.search.port.ProductSearchIndexWriter
import com.aechak.domain.product.product.enums.InspectionStatus
import com.aechak.domain.product.product.enums.SaleStatus
import kotlin.test.Test
import kotlin.test.assertEquals

/** 계약: 색인 재작성이 상품 전체를 1,000개씩 빠짐없이 한 번만 넘기고, 색인을 만든 뒤 적재하고 마지막에 끝내는지 */
class ProductSearchReindexServiceTest {
    @Test
    fun `상품을 1,000개씩 id 순서로 읽어 전부 한 번씩 색인한다`() {
        val ids = (1L..2_500L).map { it * 3 }
        val reader =
            ProductSearchDocumentReader { afterId, size -> ids.filter { it > afterId }.take(size).map(::document) }
        val writer = RecordingWriter()

        val count = ProductSearchReindexService(reader, writer).reindexAll()

        assertEquals(2_500L, count)
        assertEquals(ids, writer.indexed.flatten())
        assertEquals(listOf(1_000, 1_000, 500), writer.indexed.map { it.size })
        assertEquals(listOf("recreate", "index", "index", "index", "finish"), writer.calls)
    }

    @Test
    fun `상품이 없으면 빈 색인을 만들고 끝낸다`() {
        val writer = RecordingWriter()

        val count = ProductSearchReindexService({ _, _ -> emptyList() }, writer).reindexAll()

        assertEquals(0L, count)
        assertEquals(listOf("recreate", "finish"), writer.calls)
    }

    private class RecordingWriter : ProductSearchIndexWriter {
        val calls = mutableListOf<String>()
        val indexed = mutableListOf<List<Long>>()

        override fun recreate() {
            calls += "recreate"
        }

        override fun index(documents: List<ProductSearchDocument>) {
            calls += "index"
            indexed += documents.map { it.id }
        }

        override fun finish() {
            calls += "finish"
        }
    }

    private fun document(id: Long) =
        ProductSearchDocument(
            id = id,
            publicId = "p$id",
            name = "상품 $id",
            categoryId = 1L,
            sellerId = 1L,
            inspectionStatus = InspectionStatus.APPROVED,
            saleStatus = SaleStatus.ON_SALE,
            regularPrice = 1_000L,
            discountPrice = null,
            discountStartAt = null,
            discountEndAt = null,
            reviewCount = 0,
            representativeImageKey = null,
            sellerStatus = null,
            sellerBaseShippingFee = null,
            storeName = null,
            statsReviewCount = null,
            averageRating = null,
        )
}
