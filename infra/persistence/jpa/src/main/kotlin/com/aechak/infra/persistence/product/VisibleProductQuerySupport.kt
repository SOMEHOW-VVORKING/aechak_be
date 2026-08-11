package com.aechak.infra.persistence.product

import com.aechak.application.product.port.view.ProductCatalogView
import com.aechak.domain.product.category.QCategory
import com.aechak.domain.product.category.enums.CategoryStatus
import com.aechak.domain.product.product.QProduct
import com.aechak.domain.product.product.enums.InspectionStatus
import com.aechak.domain.product.product.enums.SaleStatus
import com.aechak.domain.product.stats.QProductStats
import com.aechak.domain.seller.seller.QSeller
import com.aechak.domain.seller.seller.enums.SellerStatus
import com.querydsl.core.BooleanBuilder
import com.querydsl.core.types.Expression
import com.querydsl.core.types.Predicate
import com.querydsl.core.types.Projections
import com.querydsl.core.types.dsl.CaseBuilder
import com.querydsl.core.types.dsl.NumberExpression
import com.querydsl.jpa.impl.JPAQuery
import com.querydsl.jpa.impl.JPAQueryFactory
import java.time.LocalDateTime

internal val product = QProduct.product
internal val category = QCategory.category
internal val parent = QCategory("parent") // 중분류 서브트리 필터, 부모 카테고리 status 검증용 별칭
internal val grandParent = QCategory("grandParent") // 조부모(대분류) status 검증용 별칭
internal val seller = QSeller.seller
internal val productStats = QProductStats.productStats // 인기순 정렬과 별점 필터, 표시용 별점/리뷰 수

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

private fun visible(): Predicate =
    BooleanBuilder()
        .and(product.inspectionStatus.eq(InspectionStatus.APPROVED))
        .and(product.saleStatus.`in`(SaleStatus.ON_SALE, SaleStatus.OUT_OF_STOCK))

private fun categoryActive(): Predicate =
    BooleanBuilder()
        .and(category.status.eq(CategoryStatus.ACTIVE))
        .and(parent.id.isNull.or(parent.status.eq(CategoryStatus.ACTIVE)))
        .and(grandParent.id.isNull.or(grandParent.status.eq(CategoryStatus.ACTIVE)))

/** 할인 기간이면 할인가, 그 외에는 정가 선택 */
internal fun effectivePrice(now: LocalDateTime): NumberExpression<Long> =
    CaseBuilder()
        .`when`(
            product.discountPrice.isNotNull
                .and(product.discountStartAt.isNull.or(product.discountStartAt.loe(now)))
                .and(product.discountEndAt.isNull.or(product.discountEndAt.goe(now))),
        ).then(product.discountPrice)
        .otherwise(product.regularPrice)

internal fun catalogViewProjection(effectivePrice: NumberExpression<Long>): Expression<ProductCatalogView> =
    Projections.constructor(
        ProductCatalogView::class.java,
        product.id,
        product.publicId,
        product.name,
        seller.storeName,
        product.representativeImageKey,
        product.regularPrice,
        product.discountPrice,
        product.discountStartAt,
        product.discountEndAt,
        effectivePrice,
        product.saleStatus,
        productStats.averageRating,
        reviewCountScore(),
    )

/** 인기순 정렬과 커서용 리뷰 수. 조인이 없거나 행이 없으면 coalesce 0 */
internal fun reviewCountScore(): NumberExpression<Int> =
    CaseBuilder()
        .`when`(productStats.reviewCount.isNull)
        .then(0)
        .otherwise(productStats.reviewCount)

/** 검색 목록 projection */
internal fun searchViewProjection(
    effectivePrice: NumberExpression<Long>,
    popularityScore: NumberExpression<Int>,
): Expression<ProductCatalogView> =
    Projections.constructor(
        ProductCatalogView::class.java,
        product.id,
        product.publicId,
        product.name,
        seller.storeName,
        product.representativeImageKey,
        product.regularPrice,
        product.discountPrice,
        product.discountStartAt,
        product.discountEndAt,
        effectivePrice,
        product.saleStatus,
        popularityScore,
        productStats.averageRating,
        reviewCountScore(),
    )
