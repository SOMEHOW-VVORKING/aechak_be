package com.aechak.infra.persistence.product.search

import com.aechak.application.product.port.view.ProductCatalogView
import com.aechak.application.product.search.port.ProductKeywordFilter
import com.aechak.application.product.search.port.ProductKeywordSearchCondition
import com.aechak.application.product.search.port.ProductKeywordSearchPort
import com.aechak.application.product.search.port.ProductKeywordSearchSort
import com.aechak.domain.product.product.enums.SaleStatus
import com.aechak.infra.persistence.product.category
import com.aechak.infra.persistence.product.effectivePrice
import com.aechak.infra.persistence.product.parent
import com.aechak.infra.persistence.product.product
import com.aechak.infra.persistence.product.productStats
import com.aechak.infra.persistence.product.reviewCountScore
import com.aechak.infra.persistence.product.searchViewProjection
import com.aechak.infra.persistence.product.seller
import com.aechak.infra.persistence.product.visibleProductQuery
import com.querydsl.core.BooleanBuilder
import com.querydsl.core.types.OrderSpecifier
import com.querydsl.core.types.Predicate
import com.querydsl.core.types.dsl.NumberExpression
import com.querydsl.jpa.impl.JPAQueryFactory
import org.springframework.stereotype.Repository
import java.time.LocalDateTime

@Repository
class ProductKeywordSearchQueryAdapter(
    private val queryFactory: JPAQueryFactory,
) : ProductKeywordSearchPort {
    override fun search(condition: ProductKeywordSearchCondition): List<ProductCatalogView> {
        val effectivePrice = effectivePrice(condition.now)
        val reviewScore = reviewCountScore()
        return visibleProductQuery(queryFactory, searchViewProjection(effectivePrice, reviewScore))
            .leftJoin(productStats)
            .on(productStats.productId.eq(product.id))
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
        now: LocalDateTime,
    ): Long =
        visibleProductQuery(queryFactory, product.count())
            .leftJoin(productStats)
            .on(productStats.productId.eq(product.id))
            .where(nameContains(filter.keyword), filterPredicate(filter, effectivePrice(now)))
            .fetchOne() ?: 0L

    override fun findIdByPublicId(publicId: String): Long? =
        queryFactory
            .select(product.id)
            .from(product)
            .where(product.publicId.eq(publicId))
            .fetchOne()

    private fun nameContains(keyword: String): Predicate = product.name.containsIgnoreCase(keyword)

    private fun filterPredicate(
        filter: ProductKeywordFilter,
        effectivePrice: NumberExpression<Long>,
    ): Predicate? {
        val builder = BooleanBuilder()
        // 중분류 필터: 해당 중분류와 그 아래 소분류 상품
        filter.categoryId?.let { builder.and(category.id.eq(it).or(parent.id.eq(it))) }
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
}
