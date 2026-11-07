package com.aechak.infra.persistence.product.search

import com.aechak.application.product.product.port.view.ProductCatalogView
import com.aechak.application.product.search.port.ProductKeywordFilter
import com.aechak.application.product.search.port.ProductKeywordSearchCondition
import com.aechak.application.product.search.port.ProductKeywordSearchFirstPage
import com.aechak.application.product.search.port.ProductKeywordSearchPort
import com.aechak.application.product.search.port.ProductKeywordSearchSort
import com.aechak.domain.product.product.QProduct
import com.aechak.domain.product.product.enums.SaleStatus
import com.aechak.domain.seller.seller.enums.SellerStatus
import com.aechak.infra.persistence.product.effectivePrice
import com.aechak.infra.persistence.product.product
import com.aechak.infra.persistence.product.productStats
import com.aechak.infra.persistence.product.searchViewProjection
import com.aechak.infra.persistence.product.seller
import com.querydsl.core.BooleanBuilder
import com.querydsl.core.types.Expression
import com.querydsl.core.types.ExpressionUtils
import com.querydsl.core.types.OrderSpecifier
import com.querydsl.core.types.Predicate
import com.querydsl.core.types.Projections
import com.querydsl.core.types.dsl.BooleanExpression
import com.querydsl.core.types.dsl.Expressions
import com.querydsl.core.types.dsl.NumberExpression
import com.querydsl.core.types.dsl.NumberPath
import com.querydsl.jpa.JPAExpressions
import com.querydsl.jpa.impl.JPAQuery
import com.querydsl.jpa.impl.JPAQueryFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Repository
import java.time.LocalDateTime

@Repository
class ProductKeywordSearchQueryAdapter(
    private val queryFactory: JPAQueryFactory,
    @Value("\${aechak.search.fulltext-enabled:false}") private val fulltextEnabled: Boolean,
    // Hibernate가 초 단위로 반올림해 500 미만이면 타임아웃이 걸리지 않음
    @Value("\${aechak.search.query-timeout-ms:3000}") private val queryTimeoutMs: Int,
    // 최근 상품 표본에서 상품명 일치 비율이 이 값보다 낮은 검색어만 FULLTEXT로 후보를 좁힘
    @Value("\${aechak.search.fulltext-max-match-ratio:0.05}") private val fulltextMaxMatchRatio: Double,
    // 최근 상품 표본에서 검색 조건 전체를 통과하는 비율이 이 값 이상이어야 정렬 인덱스 순서로 읽음
    @Value("\${aechak.search.index-walk-min-match-ratio:0.01}") private val indexWalkMinMatchRatio: Double,
) : ProductKeywordSearchPort {
    override fun search(condition: ProductKeywordSearchCondition): List<ProductCatalogView> =
        plan(condition)?.let { page(it) } ?: emptyList()

    override fun searchFirstPage(condition: ProductKeywordSearchCondition): ProductKeywordSearchFirstPage {
        val plan = plan(condition) ?: return ProductKeywordSearchFirstPage(emptyList(), 0L)
        if (plan.path == Path.INDEX_WALK) return ProductKeywordSearchFirstPage(page(plan), count(plan))
        val rows = listQuery(plan) { Projections.tuple(it, WINDOW_TOTAL) }.fetch()
        return ProductKeywordSearchFirstPage(
            fetched = rows.map { it.get(0, ProductCatalogView::class.java)!! },
            // 일치 행이 없으면 윈도 값을 실을 행도 없음
            totalCount = rows.firstOrNull()?.get(WINDOW_TOTAL) ?: 0L,
        )
    }

    override fun findIdByPublicId(publicId: String): Long? =
        queryFactory
            .select(product.id)
            .from(product)
            .where(product.publicId.eq(publicId))
            .fetchOne()

    /** 검색 대상 셀러와 읽는 경로를 정함. 검색 대상 카테고리나 셀러가 없으면 null */
    private fun plan(condition: ProductKeywordSearchCondition): SearchPlan? {
        val sellerIds = searchableSellerIds(condition.filter.freeShipping)
        if (sellerIds.isEmpty() || condition.categoryIds.isEmpty()) return null
        val sample = sample(condition, sellerIds)
        val terms = if (fulltextEnabled) ngramTerms(condition.filter.keyword) else null
        val path =
            when {
                terms != null && sample.nameRatio < fulltextMaxMatchRatio -> Path.FULLTEXT
                sample.matchRatio >= indexWalkMinMatchRatio -> Path.INDEX_WALK
                else -> Path.SCAN
            }
        return SearchPlan(condition, sellerIds, path, terms.takeIf { path == Path.FULLTEXT }, sample.discountedDenser)
    }

    /** 활성 셀러 id. 무료배송 필터면 기본 배송비 0원인 셀러만 */
    private fun searchableSellerIds(freeShipping: Boolean): List<Long> =
        queryFactory
            .select(seller.userId)
            .from(seller)
            .where(seller.status.eq(SellerStatus.ACTIVE), if (freeShipping) seller.baseShippingFee.eq(0L) else null)
            .fetch()

    /** 최근 상품 MATCH_SAMPLE_SIZE개에서 상품명 일치 수와 검색 조건 전체 통과 수를 할인 중인 상품과 나눠 셈 */
    private fun sample(
        condition: ProductKeywordSearchCondition,
        sellerIds: List<Long>,
    ): Sample {
        val newest = QProduct("newest")
        val name = product.name.contains(condition.filter.keyword)
        val matches =
            ExpressionUtils.allOf(
                name,
                *searchableProduct(condition.categoryIds, sellerIds),
                filterPredicate(condition.filter, effectivePrice(condition.now)),
            )!!
        val discounted = discountedAt(condition.now)
        val query =
            queryFactory
                .select(
                    product.id.count(),
                    countIf(name),
                    countIf(matches),
                    countIf(discounted),
                    countIf(ExpressionUtils.and(matches, discounted)),
                ).from(product)
        if (condition.filter.minRating != null) query.leftJoin(productStats).on(productStats.productId.eq(product.id))
        val row =
            query
                .where(product.id.gt(JPAExpressions.select(newest.id.max().subtract(MATCH_SAMPLE_SIZE)).from(newest)))
                .setHint(QUERY_TIMEOUT_HINT, queryTimeoutMs)
                .fetchOne()
        val counts = (0 until 5).map { row?.get(it, Long::class.javaObjectType) ?: 0L }
        return Sample(counts[0], counts[1], counts[2], counts[3], counts[4])
    }

    private fun countIf(predicate: Predicate) =
        Expressions.numberTemplate(Long::class.javaObjectType, "sum(case when {0} then 1 else 0 end)", predicate)

    /** 목록 한 페이지. 인덱스 순서 경로의 낮은 가격순만 할인 여부별 두 인덱스를 읽어 합침 */
    private fun page(plan: SearchPlan): List<ProductCatalogView> {
        if (plan.path != Path.INDEX_WALK || plan.condition.sort != ProductKeywordSearchSort.PRICE_ASC) return listQuery(plan) { it }.fetch()
        val now = plan.condition.now
        val notDiscounted =
            product.discountPrice.isNull
                .or(product.discountStartAt.gt(now))
                .or(product.discountEndAt.lt(now))
        val segments = listOf(discountedAt(now) to product.discountPrice, notDiscounted to product.regularPrice)
        val (first, second) = if (plan.discountedFirst) segments else segments.reversed()
        val firstRows = priceWalk(plan, first.first, first.second, bound = null)
        // 먼저 읽은 쪽이 한 페이지를 채우면 그 마지막 가격보다 비싼 상품은 다음 쪽에서 읽지 않음
        val bound = firstRows.takeIf { it.size >= plan.condition.limit }?.maxOf { it.sortPriceAtAnchor }
        return (firstRows + priceWalk(plan, second.first, second.second, bound))
            .sortedWith(compareBy<ProductCatalogView> { it.sortPriceAtAnchor }.thenByDescending { it.id })
            .take(plan.condition.limit)
    }

    /** 유효가격이 price와 같은 상품만 price 인덱스 순서로 읽음. keyset, 가격대, 상한도 price로 걸어 인덱스 범위로 읽음 */
    private fun priceWalk(
        plan: SearchPlan,
        segment: Predicate,
        price: NumberPath<Long>,
        bound: Long?,
    ): List<ProductCatalogView> {
        val condition = plan.condition
        val effectivePrice = effectivePrice(condition.now)
        val keyset =
            condition.lastId?.let { lastId ->
                val lastPrice = requireNotNull(condition.lastPrice)
                price.gt(lastPrice).or(price.eq(lastPrice).and(product.id.lt(lastId)))
            }
        return keywordSearchBaseQuery(queryFactory, searchViewProjection(effectivePrice, product.reviewCount))
            .where(*searchPredicates(plan, effectivePrice))
            .where(
                segment,
                keyset,
                condition.filter.minPrice?.let { price.goe(it) },
                condition.filter.maxPrice?.let { price.loe(it) },
                bound?.let { price.loe(it) },
            ).orderBy(price.asc(), product.id.desc())
            .limit(condition.limit.toLong())
            .setHint(QUERY_TIMEOUT_HINT, queryTimeoutMs)
            .fetch()
    }

    /** 검색 목록 쿼리. select로 목록 projection을 감싸 다른 열을 덧붙일 수 있음 */
    private fun <T> listQuery(
        plan: SearchPlan,
        select: (Expression<ProductCatalogView>) -> Expression<T>,
    ): JPAQuery<T> {
        val condition = plan.condition
        val effectivePrice = effectivePrice(condition.now)
        // 인덱스 순서 경로가 아니면 정렬 열에 0을 더해 옵티마이저가 정렬 인덱스로 일치 행을 찾아 헤매지 않게 함
        val reviewCount = if (plan.path == Path.INDEX_WALK) product.reviewCount else product.reviewCount.add(0)
        return keywordSearchBaseQuery(queryFactory, select(searchViewProjection(effectivePrice, product.reviewCount)))
            .where(*searchPredicates(plan, effectivePrice))
            .where(keyset(condition, effectivePrice, reviewCount))
            .orderBy(*orderBy(condition.sort, effectivePrice, reviewCount))
            .limit(condition.limit.toLong())
            .setHint(QUERY_TIMEOUT_HINT, queryTimeoutMs)
    }

    /** 인덱스 순서 경로의 첫 페이지 총개수. 셀러는 id로 거르므로 조인하지 않고 최소 평점 필터일 때만 상품 통계를 붙임 */
    private fun count(plan: SearchPlan): Long {
        val query = queryFactory.select(product.count()).from(product)
        if (plan.condition.filter.minRating != null) query.leftJoin(productStats).on(productStats.productId.eq(product.id))
        return query
            .where(*searchPredicates(plan, effectivePrice(plan.condition.now)))
            .setHint(QUERY_TIMEOUT_HINT, queryTimeoutMs)
            .fetchOne() ?: 0L
    }

    /** 상품명 조건을 맨 앞에 둔 검색 조건. 상품명에서 걸러진 행은 나머지 조건을 계산하지 않음 */
    private fun searchPredicates(
        plan: SearchPlan,
        effectivePrice: NumberExpression<Long>,
    ): Array<Predicate?> =
        arrayOf(
            nameContains(plan),
            *searchableProduct(plan.condition.categoryIds, plan.sellerIds),
            filterPredicate(plan.condition.filter, effectivePrice),
        )

    /** 상품명 부분 일치 조건. FULLTEXT 경로면 MATCH로 후보를 좁히고 LIKE로 결과를 LIKE와 같게 맞춤 */
    private fun nameContains(plan: SearchPlan): Predicate {
        val contains = product.name.contains(plan.condition.filter.keyword)
        return plan.fulltextTerms?.let { matchAgainst(it).and(contains) } ?: contains
    }

    private fun matchAgainst(terms: String) =
        Expressions
            .numberTemplate(Double::class.javaObjectType, "function('match_against',{0},{1})", product.name, Expressions.constant(terms))
            .gt(0.0)

    /** now에 할인 기간 안이라 유효가격이 할인가인 상품 */
    private fun discountedAt(now: LocalDateTime): BooleanExpression =
        product.discountPrice.isNotNull
            .and(product.discountStartAt.isNull.or(product.discountStartAt.loe(now)))
            .and(product.discountEndAt.isNull.or(product.discountEndAt.goe(now)))

    /**
     * 검색어를 불리언 모드 ngram 토큰 AND 검색어로 바꿈. 단어마다 연속한 2글자 조각을 모두 +로 요구
     * 영숫자와 _ 밖의 문자(연산자 + - * ( ) < > ~ @ " 포함)는 단어 구분으로 버림. 순서와 인접은 LIKE 재검사가 맞춤
     * ngram(토큰 2글자)이 LIKE 결과를 놓치는 검색어면 null. 2글자 미만 조각, ASCII 밖 구분 문자, 라틴 글자가 있는 경우
     */
    private fun ngramTerms(keyword: String): String? {
        val words = keyword.split(NON_WORD).filter { it.isNotEmpty() }
        val hasShortWord = words.any { it.codePointCount(0, it.length) < 2 }
        if (words.isEmpty() || hasShortWord) return null
        if (NON_ASCII_DELIMITER.containsMatchIn(keyword) || LATIN_LETTER.containsMatchIn(keyword)) return null
        return words.flatMap { bigrams(it) }.distinct().joinToString(" ") { "+$it" }
    }

    private fun bigrams(word: String): List<String> {
        val codePoints = word.codePoints().toArray()
        return (0 until codePoints.size - 1).map { String(codePoints, it, 2) }
    }

    private fun filterPredicate(
        filter: ProductKeywordFilter,
        effectivePrice: NumberExpression<Long>,
    ): Predicate? {
        val builder = BooleanBuilder()
        // 가격대: 유효가격(할인 반영) 기준
        filter.minPrice?.let { builder.and(effectivePrice.goe(it)) }
        filter.maxPrice?.let { builder.and(effectivePrice.loe(it)) }
        // 최소 평점: averageRating이 null(리뷰 없음)인 상품은 goe가 거짓이라 자연 제외
        filter.minRating?.let { builder.and(productStats.averageRating.goe(it)) }
        // 무료배송은 searchableSellerIds가 셀러 id로 거름
        // 품절 제외: 판매중만(기본은 판매중+품절 노출)
        if (filter.excludeSoldOut) builder.and(product.saleStatus.eq(SaleStatus.ON_SALE))
        return builder.takeIf { it.hasValue() }
    }

    /**
     * 커서가 가리킨 상품 다음부터 조회하는 keyset 경계 조건을 반환
     * 커서가 없는 첫 페이지면 null을 반환하고 이후 페이지면 [orderBy]와 동일한 정렬 키, id 순서의 다음 범위를 선택
     */
    private fun keyset(
        condition: ProductKeywordSearchCondition,
        effectivePrice: NumberExpression<Long>,
        reviewCount: NumberExpression<Int>,
    ): Predicate? {
        val lastId = condition.lastId ?: return null
        return when (condition.sort) {
            ProductKeywordSearchSort.POPULAR -> {
                val lastReviewCount = requireNotNull(condition.lastReviewCount)
                reviewCount
                    .lt(lastReviewCount)
                    .or(reviewCount.eq(lastReviewCount).and(product.id.lt(lastId)))
            }

            ProductKeywordSearchSort.PRICE_ASC -> {
                val lastPrice = requireNotNull(condition.lastPrice)
                effectivePrice
                    .gt(lastPrice)
                    .or(effectivePrice.eq(lastPrice).and(product.id.lt(lastId)))
            }

            ProductKeywordSearchSort.LATEST -> {
                product.id.lt(lastId)
            }
        }
    }

    /**
     * 요청한 정렬 방식에 맞는 SQL 정렬 기준을 반환
     * 인기순은 리뷰 수 내림차순, 낮은 가격순은 유효가격 오름차순, 최신순은 id 내림차순을 적용, 모두 id 내림차순으로 tie-break
     */
    private fun orderBy(
        sort: ProductKeywordSearchSort,
        effectivePrice: NumberExpression<Long>,
        reviewCount: NumberExpression<Int>,
    ): Array<OrderSpecifier<*>> =
        when (sort) {
            ProductKeywordSearchSort.POPULAR -> arrayOf(reviewCount.desc(), product.id.desc())
            ProductKeywordSearchSort.PRICE_ASC -> arrayOf(effectivePrice.asc(), product.id.desc())
            ProductKeywordSearchSort.LATEST -> arrayOf(product.id.desc())
        }

    /**
     * 읽는 경로. INDEX_WALK는 정렬 인덱스 순서로 읽다 limit 행에서 멈추고 총개수를 따로 셈
     * FULLTEXT는 MATCH로 좁힌 후보를, SCAN은 테이블 전체를 읽어 정렬하고 첫 페이지 총개수는 윈도 함수로 함께 셈
     */
    private enum class Path { INDEX_WALK, FULLTEXT, SCAN }

    private class SearchPlan(
        val condition: ProductKeywordSearchCondition,
        val sellerIds: List<Long>,
        val path: Path,
        val fulltextTerms: String?,
        val discountedFirst: Boolean,
    )

    private class Sample(
        val rows: Long,
        val nameMatches: Long,
        val matches: Long,
        val discountedRows: Long,
        val discountedMatches: Long,
    ) {
        val nameRatio = ratio(nameMatches, rows)
        val matchRatio = ratio(matches, rows)

        // 낮은 가격순 두 인덱스 중 일치 비율이 높은 쪽을 먼저 읽어야 다른 쪽을 가격 상한으로 짧게 끊을 수 있음
        val discountedDenser = ratio(discountedMatches, discountedRows) >= ratio(matches - discountedMatches, rows - discountedRows)

        private fun ratio(
            part: Long,
            whole: Long,
        ) = if (whole == 0L) 0.0 else part.toDouble() / whole
    }

    companion object {
        private const val QUERY_TIMEOUT_HINT = "jakarta.persistence.query.timeout"

        private const val MATCH_SAMPLE_SIZE = 2_000L

        // LIMIT 적용 전 일치 행 전체 수
        private val WINDOW_TOTAL = Expressions.numberTemplate(Long::class.javaObjectType, "count(*) over()")

        private val NON_WORD = Regex("[^\\p{L}\\p{Nd}_]+")

        // ngram 파서의 구분 규칙과 검색어 분리 규칙이 어긋날 수 있는 ASCII 밖 문자(가운데점, 전각 공백, 이모지 등)
        private val NON_ASCII_DELIMITER = Regex("[^\\p{L}\\p{Nd}_\\x00-\\x7F]")

        // 기본 불용어(a, at, it 등)가 든 ngram 토큰은 색인되지 않아 라틴 글자가 든 검색어는 MATCH가 일치를 놓칠 수 있음
        private val LATIN_LETTER = Regex("[A-Za-z]")
    }
}
