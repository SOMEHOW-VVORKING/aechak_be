package com.aechak.infra.persistence.product

import com.aechak.domain.product.category.QCategory
import com.aechak.domain.product.category.enums.CategoryStatus
import com.aechak.domain.product.product.QProduct
import com.aechak.domain.product.product.enums.InspectionStatus
import com.aechak.domain.product.product.enums.SaleStatus
import com.aechak.domain.seller.seller.QSeller
import com.aechak.domain.seller.seller.enums.SellerStatus
import com.querydsl.core.BooleanBuilder
import com.querydsl.core.types.Expression
import com.querydsl.core.types.Predicate
import com.querydsl.jpa.impl.JPAQuery
import com.querydsl.jpa.impl.JPAQueryFactory

internal val product = QProduct.product
internal val category = QCategory.category
internal val parent = QCategory("parent") // 중분류 서브트리 필터, 부모 카테고리 status 검증용 별칭
internal val grandParent = QCategory("grandParent") // 조부모(대분류) status 검증용 별칭
internal val seller = QSeller.seller

/** 공개 노출 조건을 적용한 상품 기본 쿼리*/
internal fun <T> visibleProductQuery(
    queryFactory: JPAQueryFactory,
    select: Expression<T>,
): JPAQuery<T> =
    queryFactory
        .select(select)
        .from(product)
        .join(seller)
        .on(seller.userId.eq(product.sellerId).and(seller.status.eq(SellerStatus.ACTIVE)))
        .join(product.category, category)
        .leftJoin(category.parent, parent)
        .leftJoin(parent.parent, grandParent)
        .where(visible(), categoryActive())

/**
 * 상품 자체가 공개 노출될 수 있는지 판정하는 조건을 반환한다.
 * 검수 승인 상태이고 판매 중 또는 품절인 상품만 통과한다. 셀러 ACTIVE 조건은 셀러 조인에서 적용한다.
 */
private fun visible(): Predicate =
    BooleanBuilder()
        .and(product.inspectionStatus.eq(InspectionStatus.APPROVED))
        .and(product.saleStatus.`in`(SaleStatus.ON_SALE, SaleStatus.OUT_OF_STOCK))

/**
 * 상품이 속한 카테고리 계층이 공개 노출 가능한지 판정하는 조건을 반환한다.
 * 상품 카테고리와 존재하는 모든 상위 카테고리가 ACTIVE인 경우만 통과한다.
 */
private fun categoryActive(): Predicate =
    BooleanBuilder()
        .and(category.status.eq(CategoryStatus.ACTIVE))
        .and(parent.id.isNull.or(parent.status.eq(CategoryStatus.ACTIVE)))
        .and(grandParent.id.isNull.or(grandParent.status.eq(CategoryStatus.ACTIVE)))
