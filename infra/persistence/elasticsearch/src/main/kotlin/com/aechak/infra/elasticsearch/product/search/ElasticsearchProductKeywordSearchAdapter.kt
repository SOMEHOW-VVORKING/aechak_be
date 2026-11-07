package com.aechak.infra.elasticsearch.product.search

import co.elastic.clients.elasticsearch.ElasticsearchAsyncClient
import co.elastic.clients.elasticsearch.ElasticsearchClient
import co.elastic.clients.elasticsearch._types.FieldValue
import co.elastic.clients.elasticsearch._types.ScriptSortType
import co.elastic.clients.elasticsearch._types.SortOptions
import co.elastic.clients.elasticsearch._types.SortOrder
import co.elastic.clients.elasticsearch._types.query_dsl.Query
import co.elastic.clients.elasticsearch.core.SearchResponse
import co.elastic.clients.json.JsonData
import com.aechak.application.product.product.port.view.ProductCatalogView
import com.aechak.application.product.search.port.ProductKeywordSearchCondition
import com.aechak.application.product.search.port.ProductKeywordSearchFirstPage
import com.aechak.application.product.search.port.ProductKeywordSearchPort
import com.aechak.application.product.search.port.ProductKeywordSearchSort
import com.aechak.domain.product.product.enums.InspectionStatus
import com.aechak.domain.product.product.enums.SaleStatus
import com.aechak.domain.seller.seller.enums.SellerStatus
import org.springframework.beans.factory.annotation.Value
import org.springframework.dao.QueryTimeoutException
import org.springframework.stereotype.Repository
import java.math.BigDecimal
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ExecutionException
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

/**
 * 키워드 상품 검색의 Elasticsearch 어댑터. JPA 어댑터와 같은 노출 조건, 필터, 정렬, keyset을 색인 위에서 계산함
 * 2글자 이상 검색어는 2글자 ngram 구문 일치, 1글자 검색어는 1글자 ngram 일치로 LIKE '%검색어%'와 같은 상품을 찾음
 */
@Repository
class ElasticsearchProductKeywordSearchAdapter(
    client: ElasticsearchClient,
    @Value("\${aechak.search.query-timeout-ms:3000}") private val queryTimeoutMs: Long,
) : ProductKeywordSearchPort {
    // 응답 대기 상한을 검색 요청에만 걸려고 비동기 클라이언트로 보내고 결과를 기다림
    private val client = ElasticsearchAsyncClient(client._transport())

    override fun search(condition: ProductKeywordSearchCondition): List<ProductCatalogView> =
        execute(condition, trackTotalHits = false)?.hits()?.hits()?.map { toView(it.source()!!, condition) } ?: emptyList()

    override fun searchFirstPage(condition: ProductKeywordSearchCondition): ProductKeywordSearchFirstPage {
        val response = execute(condition, trackTotalHits = true) ?: return ProductKeywordSearchFirstPage(emptyList(), 0L)
        return ProductKeywordSearchFirstPage(
            fetched = response.hits().hits().map { toView(it.source()!!, condition) },
            totalCount = response.hits().total()!!.value(),
        )
    }

    override fun findIdByPublicId(publicId: String): Long? =
        withTimeout {
            client.search(
                { s ->
                    s
                        .index(ProductSearchIndex.NAME)
                        .size(1)
                        .source { it.fetch(false) }
                        .query { q -> q.term { it.field("public_id").value(publicId) } }
                },
                Map::class.java,
            )
        }.hits().hits().firstOrNull()?.id()?.toLong()

    /** 검색 대상 카테고리가 없으면 null */
    private fun execute(
        condition: ProductKeywordSearchCondition,
        trackTotalHits: Boolean,
    ): SearchResponse<Map<*, *>>? {
        if (condition.categoryIds.isEmpty()) return null
        val now = ProductSearchIndex.epochMicros(condition.now)
        return withTimeout {
            client.search(
                { s ->
                    s
                        .index(ProductSearchIndex.NAME)
                        .size(condition.limit)
                        .trackTotalHits { it.enabled(trackTotalHits) }
                        .query { q -> q.bool { it.filter(filters(condition, now)) } }
                        .sort(sorts(condition.sort, now))
                    searchAfter(condition)?.let { s.searchAfter(it) }
                    s
                },
                Map::class.java,
            )
        }
    }

    private fun filters(
        condition: ProductKeywordSearchCondition,
        now: Long,
    ): List<Query> {
        val filter = condition.filter
        val saleStatuses = if (filter.excludeSoldOut) setOf(SaleStatus.ON_SALE) else SaleStatus.EXPOSABLE
        return listOfNotNull(
            nameContains(filter.keyword),
            term("inspection_status", FieldValue.of(InspectionStatus.APPROVED.name)),
            terms("sale_status", saleStatuses.map { FieldValue.of(it.name) }),
            terms("category_id", condition.categoryIds.map { FieldValue.of(it) }),
            term("seller_status", FieldValue.of(SellerStatus.ACTIVE.name)),
            if (filter.freeShipping) term("seller_base_shipping_fee", FieldValue.of(0L)) else null,
            filter.minRating?.let { range("average_rating", gte = JsonData.of(it)) },
            if (filter.minPrice != null || filter.maxPrice != null) effectivePriceBetween(filter.minPrice, filter.maxPrice, now) else null,
        )
    }

    private fun nameContains(keyword: String): Query =
        if (keyword.codePointCount(0, keyword.length) >= 2) {
            Query.of { q -> q.matchPhrase { it.field("name").query(keyword) } }
        } else {
            Query.of { q -> q.match { it.field("name.unigram").query(keyword) } }
        }

    /** 할인 기간 안이면 할인가, 아니면 정가가 범위 안인 상품 */
    private fun effectivePriceBetween(
        min: Long?,
        max: Long?,
        now: Long,
    ): Query {
        val discounted = discountedAt(now)
        val inRange = { field: String -> range(field, gte = min?.let(JsonData::of), lte = max?.let(JsonData::of)) }
        return bool {
            it
                .should(bool { b -> b.filter(discounted, inRange("discount_price")) })
                .should(bool { b -> b.filter(inRange("regular_price")).mustNot(discounted) })
                .minimumShouldMatch("1")
        }
    }

    private fun discountedAt(now: Long): Query =
        bool {
            it.filter(
                exists("discount_price"),
                missingOr("discount_start_at", range("discount_start_at", lte = JsonData.of(now))),
                missingOr("discount_end_at", range("discount_end_at", gte = JsonData.of(now))),
            )
        }

    private fun missingOr(
        field: String,
        query: Query,
    ): Query = bool { it.should(bool { b -> b.mustNot(exists(field)) }, query).minimumShouldMatch("1") }

    private fun sorts(
        sort: ProductKeywordSearchSort,
        now: Long,
    ): List<SortOptions> {
        val idDesc = SortOptions.of { s -> s.field { it.field("id").order(SortOrder.Desc) } }
        return when (sort) {
            ProductKeywordSearchSort.POPULAR -> {
                listOf(SortOptions.of { s -> s.field { it.field("review_count").order(SortOrder.Desc) } }, idDesc)
            }

            ProductKeywordSearchSort.PRICE_ASC -> {
                val effectivePrice =
                    SortOptions.of { s ->
                        s.script {
                            it
                                .type(ScriptSortType.Number)
                                .order(SortOrder.Asc)
                                .script { script ->
                                    script
                                        .source { src ->
                                            src.scriptString(EFFECTIVE_PRICE_SCRIPT)
                                        }.params("now", JsonData.of(now))
                                }
                        }
                    }
                listOf(effectivePrice, idDesc)
            }

            ProductKeywordSearchSort.LATEST -> {
                listOf(idDesc)
            }
        }
    }

    /** 커서 앵커를 정렬 키 순서의 search_after 값으로 바꿈. 첫 페이지면 null */
    private fun searchAfter(condition: ProductKeywordSearchCondition): List<FieldValue>? {
        val lastId = condition.lastId ?: return null
        return when (condition.sort) {
            ProductKeywordSearchSort.POPULAR -> {
                listOf(
                    FieldValue.of(requireNotNull(condition.lastReviewCount).toLong()),
                    FieldValue.of(lastId),
                )
            }

            ProductKeywordSearchSort.PRICE_ASC -> {
                listOf(FieldValue.of(requireNotNull(condition.lastPrice)), FieldValue.of(lastId))
            }

            ProductKeywordSearchSort.LATEST -> {
                listOf(FieldValue.of(lastId))
            }
        }
    }

    private fun toView(
        source: Map<*, *>,
        condition: ProductKeywordSearchCondition,
    ): ProductCatalogView {
        val now = ProductSearchIndex.epochMicros(condition.now)
        val regularPrice = source.long("regular_price")!!
        val discountPrice = source.long("discount_price")
        val start = source.long("discount_start_at")
        val end = source.long("discount_end_at")
        val discounted = discountPrice != null && (start == null || start <= now) && (end == null || end >= now)
        return ProductCatalogView(
            id = source.long("id")!!,
            publicId = source["public_id"] as String,
            name = source["name"] as String,
            sellerName = source["store_name"] as String,
            representativeImageKey = source["representative_image_key"] as String?,
            regularPrice = regularPrice,
            discountPrice = discountPrice,
            discountStartAt = start?.let(ProductSearchIndex::fromEpochMicros),
            discountEndAt = end?.let(ProductSearchIndex::fromEpochMicros),
            sortPriceAtAnchor = if (discounted) discountPrice!! else regularPrice,
            saleStatus = SaleStatus.valueOf(source["sale_status"] as String),
            popularityScore = source.long("review_count")!!.toInt(),
            averageRating = (source["average_rating"] as String?)?.let(::BigDecimal),
            reviewCount = source.long("stats_review_count")?.toInt() ?: 0,
            isViewable = true,
        )
    }

    /** queryTimeoutMs 안에 응답이 없으면 요청을 취소하고 QueryTimeoutException을 던짐 */
    private fun <T> withTimeout(request: () -> CompletableFuture<T>): T {
        val future = request()
        return try {
            future.get(queryTimeoutMs, TimeUnit.MILLISECONDS)
        } catch (e: TimeoutException) {
            future.cancel(true)
            throw QueryTimeoutException("검색 엔진 응답 시간 초과", e)
        } catch (e: ExecutionException) {
            throw e.cause ?: e
        }
    }

    private fun Map<*, *>.long(key: String): Long? = (this[key] as Number?)?.toLong()

    private fun bool(build: (co.elastic.clients.elasticsearch._types.query_dsl.BoolQuery.Builder) -> Any): Query =
        Query.of { q ->
            q.bool {
                build(it)
                it
            }
        }

    private fun term(
        field: String,
        value: FieldValue,
    ): Query = Query.of { q -> q.term { it.field(field).value(value) } }

    private fun terms(
        field: String,
        values: List<FieldValue>,
    ): Query = Query.of { q -> q.terms { t -> t.field(field).terms { it.value(values) } } }

    private fun exists(field: String): Query = Query.of { q -> q.exists { it.field(field) } }

    private fun range(
        field: String,
        gte: JsonData? = null,
        lte: JsonData? = null,
    ): Query = Query.of { q -> q.range { r -> r.untyped { it.field(field).gte(gte).lte(lte) } } }

    companion object {
        private val EFFECTIVE_PRICE_SCRIPT =
            """
            if (doc['discount_price'].size() != 0
                && (doc['discount_start_at'].size() == 0 || doc['discount_start_at'].value <= params.now)
                && (doc['discount_end_at'].size() == 0 || doc['discount_end_at'].value >= params.now)) {
              return doc['discount_price'].value;
            }
            return doc['regular_price'].value;
            """.trimIndent()
    }
}
