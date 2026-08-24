package com.aechak.infra.persistence.product

import com.aechak.application.product.product.port.view.ProductCatalogView
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
import com.querydsl.core.types.dsl.BooleanExpression
import com.querydsl.core.types.dsl.CaseBuilder
import com.querydsl.core.types.dsl.Expressions
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
        .where(visible(), categoryChainActive())

private fun visible(): Predicate =
    BooleanBuilder()
        .and(product.inspectionStatus.eq(InspectionStatus.APPROVED))
        .and(product.saleStatus.`in`(SaleStatus.EXPOSABLE))

/**
 * 카테고리 체인(자신·부모·조부모)이 모두 활성인지.
 * 카탈로그·검색은 이 조건을 where 필터로 써서 비활성 상품을 숨기고,
 * 찜 목록은 같은 조건을 진입 가능 여부 컬럼(CASE)으로 재사용한다. 규칙을 한곳에 둬 두 경로가 어긋나지 않게 한다.
 */
internal fun categoryChainActive(): BooleanExpression =
    category.status
        .eq(CategoryStatus.ACTIVE)
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
        // 카탈로그·검색은 categoryChainActive() 필터로 활성 상품만 통과하므로 진입 가능은 항상 참
        Expressions.asBoolean(true),
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
        // 활성 카테고리만 통과하므로 진입 가능은 항상 참
        Expressions.asBoolean(true),
    )
