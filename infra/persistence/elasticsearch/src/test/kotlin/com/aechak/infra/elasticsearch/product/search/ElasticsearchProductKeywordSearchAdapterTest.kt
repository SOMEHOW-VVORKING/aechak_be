package com.aechak.infra.elasticsearch.product.search

import co.elastic.clients.elasticsearch.ElasticsearchClient
import co.elastic.clients.json.jackson.Jackson3JsonpMapper
import co.elastic.clients.transport.rest5_client.Rest5ClientTransport
import co.elastic.clients.transport.rest5_client.low_level.Rest5Client
import com.aechak.application.product.search.port.ProductKeywordFilter
import com.aechak.application.product.search.port.ProductKeywordSearchCondition
import com.aechak.application.product.search.port.ProductKeywordSearchSort
import org.apache.hc.core5.http.HttpHost
import org.springframework.dao.QueryTimeoutException
import java.net.ServerSocket
import java.net.Socket
import java.time.LocalDateTime
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import kotlin.concurrent.thread
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * 계약: 검색 엔진이 응답하지 않으면 어댑터가 QueryTimeoutException을 던져 서비스가 503으로 바꿀 수 있는지
 * 동시 요청 상한이 차 있으면 요청을 보내지 않고 바로 실패하고, 시간을 넘긴 요청은 끝날 때까지 자리를 쥐는지
 */
class ElasticsearchProductKeywordSearchAdapterTest {
    @Test
    fun `응답 대기 상한을 넘기면 QueryTimeoutException을 던진다`() {
        ServerSocket(0).use { server ->
            // 연결만 받고 응답하지 않는 서버. 받은 소켓을 들고 있어 연결이 닫히지 않게 함
            val accepted = CopyOnWriteArrayList<Socket>()
            thread(isDaemon = true) { runCatching { while (true) accepted += server.accept() } }
            Rest5Client.builder(HttpHost("http", "localhost", server.localPort)).build().use { restClient ->
                val client = ElasticsearchClient(Rest5ClientTransport(restClient, Jackson3JsonpMapper()))
                val adapter = ElasticsearchProductKeywordSearchAdapter(client, queryTimeoutMs = 200, maxConcurrentRequests = 4)

                assertFailsWith<QueryTimeoutException> { adapter.findIdByPublicId("01JD5G7HJK8M2N4PQ6RSTV9WXY") }
                // 남은 요청을 끝내 클라이언트를 닫을 때 기다리지 않게 함
                accepted.forEach { it.close() }
            }
        }
    }

    @Test
    fun `동시 요청 상한이 차 있으면 바로 실패하고 시간을 넘긴 요청은 끝날 때까지 자리를 쥔다`() {
        ServerSocket(0).use { server ->
            // 응답하지 않는 서버. 연결 4개를 받으면 알림
            val accepted = CopyOnWriteArrayList<Socket>()
            val fourAccepted = CountDownLatch(4)
            thread(isDaemon = true) {
                runCatching {
                    while (true) {
                        accepted += server.accept()
                        fourAccepted.countDown()
                    }
                }
            }
            Rest5Client.builder(HttpHost("http", "localhost", server.localPort)).build().use { restClient ->
                val client = ElasticsearchClient(Rest5ClientTransport(restClient, Jackson3JsonpMapper()))
                val adapter = ElasticsearchProductKeywordSearchAdapter(client, queryTimeoutMs = 300, maxConcurrentRequests = 4)
                // 가격 필터를 건 낮은 가격순 첫 페이지는 결과 2개와 개수 2개로 요청 4개를 보내 자리를 모두 씀
                val first = assertFailsWith<QueryTimeoutException> { adapter.searchFirstPage(priceAscFirstPageWithPriceFilter()) }
                assertIs<TimeoutException>(first.cause)
                assertTrue(fourAccepted.await(5, TimeUnit.SECONDS), "요청 4개가 서버에 닿지 않았다")

                // 시간을 넘긴 요청을 취소하지 않으므로 자리가 그대로이고 다음 요청은 보내지 않고 거절함
                val rejected = assertFailsWith<QueryTimeoutException> { adapter.findIdByPublicId("01JD5G7HJK8M2N4PQ6RSTV9WXY") }
                assertNull(rejected.cause)

                // 서버가 연결을 닫으면 요청이 끝나 자리가 돌아오고 다음 요청은 보낸 뒤 응답을 기다리다 시간을 넘김
                accepted.forEach { it.close() }
                val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5)
                var sent: QueryTimeoutException
                do {
                    sent = assertFailsWith<QueryTimeoutException> { adapter.findIdByPublicId("01JD5G7HJK8M2N4PQ6RSTV9WXY") }
                    if (sent.cause == null) Thread.sleep(20)
                } while (sent.cause == null && System.nanoTime() < deadline)
                assertIs<TimeoutException>(sent.cause)
                // 남은 요청을 끝내 클라이언트를 닫을 때 기다리지 않게 함
                accepted.forEach { it.close() }
            }
        }
    }

    private fun priceAscFirstPageWithPriceFilter() =
        ProductKeywordSearchCondition(
            filter =
                ProductKeywordFilter(
                    keyword = "강아지",
                    minPrice = 1_000L,
                    maxPrice = 50_000L,
                    minRating = null,
                    categoryId = null,
                    freeShipping = false,
                    excludeSoldOut = false,
                ),
            categoryIds = setOf(1L),
            sort = ProductKeywordSearchSort.PRICE_ASC,
            lastId = null,
            lastReviewCount = null,
            lastPrice = null,
            limit = 21,
            now = LocalDateTime.of(2026, 11, 7, 12, 0),
        )
}
