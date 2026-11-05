package com.aechak.infra.elasticsearch.product.search

import co.elastic.clients.elasticsearch.ElasticsearchClient
import com.aechak.application.product.search.port.ProductSearchDocument
import com.aechak.application.product.search.port.ProductSearchIndexWriter
import org.springframework.core.io.ClassPathResource
import org.springframework.stereotype.Repository

/** 상품 검색 색인 재작성. 적재 중에는 refresh를 끄고 끝에 한 번 refresh함 */
@Repository
class ProductSearchIndexWriteAdapter(
    private val client: ElasticsearchClient,
) : ProductSearchIndexWriter {
    override fun recreate() {
        client.indices().delete { it.index(ProductSearchIndex.NAME).ignoreUnavailable(true) }
        ClassPathResource(ProductSearchIndex.MAPPING_RESOURCE).inputStream.use { mapping ->
            client.indices().create { it.index(ProductSearchIndex.NAME).withJson(mapping) }
        }
        putRefreshInterval("-1")
    }

    override fun index(documents: List<ProductSearchDocument>) {
        if (documents.isEmpty()) return
        val response =
            client.bulk { bulk ->
                documents.forEach { document ->
                    bulk.operations { op ->
                        op.index {
                            it
                                .index(
                                    ProductSearchIndex.NAME,
                                ).id(document.id.toString())
                                .document(ProductSearchIndex.toSource(document))
                        }
                    }
                }
                bulk
            }
        check(!response.errors()) {
            "검색 색인 적재 실패: ${response.items().firstNotNullOfOrNull { it.error() }?.reason()}"
        }
    }

    override fun finish() {
        putRefreshInterval("1s")
        client.indices().refresh { it.index(ProductSearchIndex.NAME) }
    }

    private fun putRefreshInterval(interval: String) {
        client.indices().putSettings { request ->
            request.index(ProductSearchIndex.NAME).settings { it.refreshInterval { time -> time.time(interval) } }
        }
    }
}
