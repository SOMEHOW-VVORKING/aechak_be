package com.aechak.infra.elasticsearch.product.search

import co.elastic.clients.elasticsearch.ElasticsearchClient
import co.elastic.clients.json.jackson.Jackson3JsonpMapper
import co.elastic.clients.transport.rest5_client.Rest5ClientTransport
import co.elastic.clients.transport.rest5_client.low_level.Rest5Client
import org.apache.hc.core5.http.HttpHost
import org.springframework.dao.QueryTimeoutException
import java.net.ServerSocket
import java.net.Socket
import kotlin.concurrent.thread
import kotlin.test.Test
import kotlin.test.assertFailsWith

/** 계약: 검색 엔진이 응답하지 않으면 어댑터가 QueryTimeoutException을 던져 서비스가 503으로 바꿀 수 있는지 */
class ElasticsearchProductKeywordSearchAdapterTest {
    @Test
    fun `응답 대기 상한을 넘기면 QueryTimeoutException을 던진다`() {
        ServerSocket(0).use { server ->
            // 연결만 받고 응답하지 않는 서버. 받은 소켓을 들고 있어 연결이 닫히지 않게 함
            val accepted = mutableListOf<Socket>()
            thread(isDaemon = true) { runCatching { while (true) accepted += server.accept() } }
            Rest5Client.builder(HttpHost("http", "localhost", server.localPort)).build().use { restClient ->
                val client = ElasticsearchClient(Rest5ClientTransport(restClient, Jackson3JsonpMapper()))
                val adapter = ElasticsearchProductKeywordSearchAdapter(client, queryTimeoutMs = 200)

                assertFailsWith<QueryTimeoutException> { adapter.findIdByPublicId("01JD5G7HJK8M2N4PQ6RSTV9WXY") }
            }
        }
    }
}
