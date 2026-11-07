package com.aechak.infra.persistence.product.search

import com.aechak.domain.product.product.enums.InspectionStatus
import com.aechak.domain.product.product.enums.SaleStatus
import com.aechak.infra.persistence.product.product
import com.aechak.infra.persistence.product.productStats
import com.aechak.infra.persistence.product.seller
import com.querydsl.core.types.Expression
import com.querydsl.core.types.Predicate
import com.querydsl.jpa.impl.JPAQuery
import com.querydsl.jpa.impl.JPAQueryFactory

/** 검색 전용 기본 쿼리. 옵티마이저가 상품부터 읽도록 셀러 상호와 상품 통계는 왼쪽 조인으로만 붙임 */
internal fun <T> keywordSearchBaseQuery(
    queryFactory: JPAQueryFactory,
    select: Expression<T>,
): JPAQuery<T> =
    queryFactory
        .select(select)
        .from(product)
        .leftJoin(seller)
        .on(seller.userId.eq(product.sellerId))
        .leftJoin(productStats)
        .on(productStats.productId.eq(product.id))

/**
 * 검색 노출 조건. 검수 승인, 판매중이나 품절, 검색 대상 카테고리와 셀러
 * categoryIds는 자신, 부모, 조부모가 모두 활성인 카테고리 id, sellerIds는 활성 셀러 id
 */
internal fun searchableProduct(
    categoryIds: Collection<Long>,
    sellerIds: Collection<Long>,
): Array<Predicate> =
    arrayOf(
        product.inspectionStatus.eq(InspectionStatus.APPROVED),
        product.saleStatus.`in`(SaleStatus.EXPOSABLE),
        product.category.id.`in`(categoryIds),
        product.sellerId.`in`(sellerIds),
    )
