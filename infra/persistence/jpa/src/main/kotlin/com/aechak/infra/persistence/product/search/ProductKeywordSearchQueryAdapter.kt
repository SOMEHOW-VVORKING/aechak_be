package com.aechak.infra.persistence.product.search

import com.aechak.application.product.product.port.view.ProductCatalogView
import com.aechak.application.product.search.port.ProductKeywordFilter
import com.aechak.application.product.search.port.ProductKeywordSearchCondition
import com.aechak.application.product.search.port.ProductKeywordSearchPort
import com.aechak.application.product.search.port.ProductKeywordSearchSort
import com.aechak.domain.product.product.enums.SaleStatus
import com.aechak.infra.persistence.product.effectivePrice
import com.aechak.infra.persistence.product.product
import com.aechak.infra.persistence.product.productStats
import com.aechak.infra.persistence.product.reviewCountScore
import com.aechak.infra.persistence.product.searchViewProjection
import com.aechak.infra.persistence.product.seller
import com.querydsl.core.BooleanBuilder
import com.querydsl.core.types.OrderSpecifier
import com.querydsl.core.types.Predicate
import com.querydsl.core.types.dsl.Expressions
import com.querydsl.core.types.dsl.NumberExpression
import com.querydsl.jpa.impl.JPAQueryFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Repository
import java.time.LocalDateTime

@Repository
class ProductKeywordSearchQueryAdapter(
    private val queryFactory: JPAQueryFactory,
    @Value("\${aechak.search.fulltext-enabled:false}") private val fulltextEnabled: Boolean,
) : ProductKeywordSearchPort {
    override fun search(condition: ProductKeywordSearchCondition): List<ProductCatalogView> {
        val effectivePrice = effectivePrice(condition.now)
        val reviewScore = reviewCountScore()
        return keywordSearchBaseQuery(queryFactory, searchViewProjection(effectivePrice, reviewScore), condition.categoryIds)
            .where(
                nameContains(condition.filter.keyword),
                filterPredicate(condition.filter, effectivePrice),
                keyset(condition, effectivePrice, reviewScore),
            ).orderBy(*orderBy(condition.sort, effectivePrice, reviewScore))
            .limit(condition.limit.toLong())
            .fetch()
    }

    override fun countMatching(
        filter: ProductKeywordFilter,
        categoryIds: Set<Long>,
        now: LocalDateTime,
    ): Long =
        keywordSearchBaseQuery(queryFactory, product.count(), categoryIds)
            .where(nameContains(filter.keyword), filterPredicate(filter, effectivePrice(now)))
            .fetchOne() ?: 0L

    override fun findIdByPublicId(publicId: String): Long? =
        queryFactory
            .select(product.id)
            .from(product)
            .where(product.publicId.eq(publicId))
            .fetchOne()

    /**
     * 상품명 부분 일치 조건. FULLTEXT가 켜져 있고 phrase를 만들 수 있으면 MATCH로 후보를 좁히고 LIKE로 결과를 LIKE와 같게 맞춤
     */
    private fun nameContains(keyword: String): Predicate {
        val contains = product.name.contains(keyword)
        val phrase = if (fulltextEnabled) fulltextPhrase(keyword) else null
        return phrase?.let { matchAgainst(it).and(contains) } ?: contains
    }

    private fun matchAgainst(phrase: String) =
        Expressions
            .numberTemplate(Double::class.javaObjectType, "function('match_against',{0},{1})", product.name, Expressions.constant(phrase))
            .gt(0.0)

    /**
     * 검색어를 불리언 모드 구문 검색 phrase로 바꿈. 영숫자와 _ 밖의 문자(연산자 + - * ( ) < > ~ @ " 포함)는 공백 하나로 바꿈
     * ngram(토큰 2글자)이 LIKE 결과를 놓치는 검색어면 null. 2글자 미만 조각, ASCII 밖 구분 문자, 라틴 글자가 있는 경우
     */
    private fun fulltextPhrase(keyword: String): String? {
        val words = keyword.split(NON_WORD).filter { it.isNotEmpty() }
        val hasShortWord = words.any { it.codePointCount(0, it.length) < 2 }
        if (words.isEmpty() || hasShortWord) return null
        if (NON_ASCII_DELIMITER.containsMatchIn(keyword) || LATIN_LETTER.containsMatchIn(keyword)) return null
        return words.joinToString(" ", "\"", "\"")
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
        // 무료배송: 셀러 기본 배송비 0
        if (filter.freeShipping) builder.and(seller.baseShippingFee.eq(0L))
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
        reviewScore: NumberExpression<Int>,
    ): Predicate? {
        val lastId = condition.lastId ?: return null
        return when (condition.sort) {
            ProductKeywordSearchSort.POPULAR -> {
                val lastReviewCount = requireNotNull(condition.lastReviewCount)
                reviewScore
                    .lt(lastReviewCount)
                    .or(reviewScore.eq(lastReviewCount).and(product.id.lt(lastId)))
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
        reviewScore: NumberExpression<Int>,
    ): Array<OrderSpecifier<*>> =
        when (sort) {
            ProductKeywordSearchSort.POPULAR -> arrayOf(reviewScore.desc(), product.id.desc())
            ProductKeywordSearchSort.PRICE_ASC -> arrayOf(effectivePrice.asc(), product.id.desc())
            ProductKeywordSearchSort.LATEST -> arrayOf(product.id.desc())
        }

    companion object {
        private val NON_WORD = Regex("[^\\p{L}\\p{Nd}_]+")

        // ngram이 구분 문자로 보지만 구문 검색에서 앞뒤 조각의 일치를 놓치는 ASCII 밖 문자(가운데점, 전각 공백, 이모지 등)
        private val NON_ASCII_DELIMITER = Regex("[^\\p{L}\\p{Nd}_\\x00-\\x7F]")

        // 기본 불용어(a, at, it 등)가 든 ngram 토큰은 색인되지 않아 라틴 글자가 든 검색어는 MATCH가 일치를 놓칠 수 있음
        private val LATIN_LETTER = Regex("[A-Za-z]")
    }
}
