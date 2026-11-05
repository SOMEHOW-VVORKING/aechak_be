package com.aechak.infra.persistence.product.search

import com.aechak.domain.product.product.enums.InspectionStatus
import com.aechak.domain.product.product.enums.SaleStatus
import com.aechak.domain.seller.seller.enums.SellerStatus
import com.aechak.infra.persistence.product.product
import com.aechak.infra.persistence.product.productStats
import com.aechak.infra.persistence.product.seller
import com.querydsl.core.types.Expression
import com.querydsl.jpa.impl.JPAQuery
import com.querydsl.jpa.impl.JPAQueryFactory

/**
 * 검색 전용 기본 쿼리. 활성 셀러와 상품 통계만 조인하고 노출 카테고리는 categoryIds로 제한
 * categoryIds는 자신, 부모, 조부모가 모두 활성인 카테고리 id 목록
 */
internal fun <T> keywordSearchBaseQuery(
    queryFactory: JPAQueryFactory,
    select: Expression<T>,
    categoryIds: Collection<Long>,
): JPAQuery<T> =
    queryFactory
        .select(select)
        .from(product)
        .join(seller)
        .on(seller.userId.eq(product.sellerId).and(seller.status.eq(SellerStatus.ACTIVE)))
        .leftJoin(productStats)
        .on(productStats.productId.eq(product.id))
        .where(
            product.inspectionStatus.eq(InspectionStatus.APPROVED),
            product.saleStatus.`in`(SaleStatus.EXPOSABLE),
            product.category.id.`in`(categoryIds),
        )
