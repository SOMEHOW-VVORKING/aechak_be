package com.aechak.infra.elasticsearch.product.search

import co.elastic.clients.transport.rest5_client.low_level.Rest5ClientBuilder
import org.apache.hc.client5.http.impl.nio.PoolingAsyncClientConnectionManagerBuilder
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.elasticsearch.autoconfigure.Rest5ClientBuilderCustomizer
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

/** ES 클라이언트 연결 수를 검색 동시 요청 상한과 같게 둠 */
@Configuration
class ElasticsearchConnectionPoolConfig {
    @Bean
    fun searchConnectionPoolCustomizer(
        @Value("\${aechak.search.max-concurrent-requests:40}") maxConcurrentRequests: Int,
    ): Rest5ClientBuilderCustomizer =
        object : Rest5ClientBuilderCustomizer {
            override fun customize(builder: Rest5ClientBuilder) = Unit

            override fun customize(builder: PoolingAsyncClientConnectionManagerBuilder) {
                builder.setMaxConnPerRoute(maxConcurrentRequests).setMaxConnTotal(maxConcurrentRequests)
            }
        }
}
