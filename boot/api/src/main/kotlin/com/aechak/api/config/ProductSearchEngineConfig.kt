package com.aechak.api.config

import com.aechak.application.product.search.port.ProductKeywordSearchPort
import com.aechak.application.product.search.port.ProductSearchDocumentReader
import com.aechak.application.product.search.port.ProductSearchIndexWriter
import com.aechak.application.product.search.service.ProductSearchReindexService
import com.aechak.infra.elasticsearch.product.search.ElasticsearchProductKeywordSearchAdapter
import com.aechak.infra.persistence.product.search.ProductKeywordSearchQueryAdapter
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.ApplicationRunner
import org.springframework.boot.autoconfigure.condition.ConditionalOnBooleanProperty
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Primary

/** 키워드 검색 엔진 선택과 Elasticsearch 색인 재작성 조립 */
@Configuration
class ProductSearchEngineConfig {
    /** aechak.search.engine=es면 Elasticsearch, 아니면 JPA 어댑터로 검색 */
    @Bean
    @Primary
    fun productKeywordSearchPort(
        jpa: ProductKeywordSearchQueryAdapter,
        elasticsearch: ElasticsearchProductKeywordSearchAdapter,
        @Value("\${aechak.search.engine:jpa}") engine: String,
    ): ProductKeywordSearchPort = if (engine == "es") elasticsearch else jpa

    @Bean
    fun productSearchReindexService(
        reader: ProductSearchDocumentReader,
        writer: ProductSearchIndexWriter,
    ) = ProductSearchReindexService(reader, writer)

    /** aechak.search.es.reindex-on-startup=true면 시작 직후 색인을 한 번 다시 만듦 */
    @Bean
    @ConditionalOnBooleanProperty("aechak.search.es.reindex-on-startup")
    fun productSearchReindexOnStartup(reindexService: ProductSearchReindexService) = ApplicationRunner { reindexService.reindexAll() }
}
