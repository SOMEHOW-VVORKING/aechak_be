package com.aechak.infra.persistence.product

import com.aechak.application.product.product.port.SellerProductCondition
import com.aechak.application.product.product.port.SellerProductQueryPort
import com.aechak.application.product.product.port.SellerProductSort
import com.aechak.application.product.product.port.SellerProductStockFilter
import com.aechak.application.product.product.port.view.SellerProductOptionView
import com.aechak.application.product.product.port.view.SellerProductOwnershipView
import com.aechak.application.product.product.port.view.SellerProductView
import com.aechak.domain.product.option.QOptionCombination
import com.aechak.domain.product.product.enums.InspectionStatus
import com.aechak.domain.product.product.enums.SaleStatus
import com.querydsl.core.types.Expression
import com.querydsl.core.types.OrderSpecifier
import com.querydsl.core.types.Predicate
import com.querydsl.core.types.Projections
import com.querydsl.core.types.dsl.Expressions
import com.querydsl.core.types.dsl.NumberExpression
import com.querydsl.jpa.JPAExpressions
import com.querydsl.jpa.impl.JPAQuery
import com.querydsl.jpa.impl.JPAQueryFactory
import org.springframework.stereotype.Repository
import java.time.LocalDateTime

private val optionCombination = QOptionCombination.optionCombination

/**
 * 셀러 자신의 상품 목록·옵션 재고 조회를 QueryDSL로 수행한다.
 * 공개 카탈로그와 달리 노출 조건 없이 sellerId로 잠근 전체 상품을 조회하고,
 * 재고는 판매 상태가 아니라 활성 옵션 조합의 재고 원천 데이터로 판정한다.
 */
@Repository
class SellerProductQueryAdapter(
    private val queryFactory: JPAQueryFactory,
) : SellerProductQueryPort {
    override fun findPage(condition: SellerProductCondition): List<SellerProductView> =
        baseQuery(condition, viewProjection())
            .orderBy(*orderBy(condition.sort, effectivePrice(condition.now)))
            .offset(condition.offset)
            .limit(condition.limit.toLong())
            .fetch()

    override fun count(condition: SellerProductCondition): Long = baseQuery(condition, product.count()).fetchOne() ?: 0L

    override fun findOwnership(publicId: String): SellerProductOwnershipView? =
        queryFactory
            .select(Projections.constructor(SellerProductOwnershipView::class.java, product.id, product.sellerId))
            .from(product)
            .where(product.publicId.eq(publicId))
            .fetchOne()

    override fun findCombinations(productId: Long): List<SellerProductOptionView> =
        queryFactory
            .select(
                Projections.constructor(
                    SellerProductOptionView::class.java,
                    optionCombination.id,
                    optionCombination.name,
                    optionCombination.additionalPrice,
                    optionCombination.stockQuantity,
                    optionCombination.isActive,
                ),
            ).from(optionCombination)
            .where(optionCombination.product.id.eq(productId))
            .orderBy(optionCombination.id.asc())
            .fetch()

    /** 목록·카운트가 공유하는 조인·필터 골격. 카테고리 조인은 필터와 카테고리명 조회에 쓴다 */
    private fun <T> baseQuery(
        condition: SellerProductCondition,
        select: Expression<T>,
    ): JPAQuery<T> =
        queryFactory
            .select(select)
            .from(product)
            .join(product.category, category)
            .leftJoin(category.parent, parent)
            .leftJoin(parent.parent, grandParent)
            .where(
                product.sellerId.eq(condition.sellerId),
                keywordContains(condition.keyword),
                saleStatusIn(condition.saleStatuses),
                inspectionStatusIn(condition.inspectionStatuses),
                categorySubtree(condition.categoryId),
                createdFrom(condition.createdFrom),
                createdBefore(condition.createdToExclusive),
                stockFilter(condition.stockFilter),
            )

    private fun viewProjection(): Expression<SellerProductView> =
        Projections.constructor(
            SellerProductView::class.java,
            product.id,
            product.publicId,
            product.name,
            product.representativeImageKey,
            category.name,
            product.regularPrice,
            product.discountPrice,
            product.discountStartAt,
            product.discountEndAt,
            product.saleStatus,
            product.inspectionStatus,
            activeStockSum(),
            product.createdAt,
        )

    /** 활성 조합 재고 합 — 조합이 전부 비활성이면 0 */
    private fun activeStockSum(): Expression<Long> =
        JPAExpressions
            .select(Expressions.numberTemplate(Long::class.javaObjectType, "coalesce(sum({0}), 0)", optionCombination.stockQuantity))
            .from(optionCombination)
            .where(optionCombination.product.eq(product), optionCombination.isActive.isTrue)

    private fun keywordContains(keyword: String?): Predicate? = keyword?.let { product.name.containsIgnoreCase(it) }

    private fun saleStatusIn(statuses: List<SaleStatus>): Predicate? =
        statuses.takeIf { it.isNotEmpty() }?.let { product.saleStatus.`in`(it) }

    private fun inspectionStatusIn(statuses: List<InspectionStatus>): Predicate? =
        statuses.takeIf { it.isNotEmpty() }?.let { product.inspectionStatus.`in`(it) }

    /** 지목한 카테고리가 소분류면 그 자체, 중·대분류면 하위까지 포함해 조회하는 조건 */
    private fun categorySubtree(categoryId: Long?): Predicate? =
        categoryId?.let {
            category.id
                .eq(it)
                .or(parent.id.eq(it))
                .or(grandParent.id.eq(it))
        }

    private fun createdFrom(from: LocalDateTime?): Predicate? = from?.let { product.createdAt.goe(it) }

    private fun createdBefore(toExclusive: LocalDateTime?): Predicate? = toExclusive?.let { product.createdAt.lt(it) }

    /** 재고 있음/소진 — 재고가 남은 활성 조합의 존재 여부로 판정 */
    private fun stockFilter(filter: SellerProductStockFilter?): Predicate? {
        if (filter == null) return null
        val hasActiveStock =
            JPAExpressions
                .selectOne()
                .from(optionCombination)
                .where(
                    optionCombination.product.eq(product),
                    optionCombination.isActive.isTrue,
                    optionCombination.stockQuantity.gt(0),
                ).exists()
        return when (filter) {
            SellerProductStockFilter.IN_STOCK -> hasActiveStock
            SellerProductStockFilter.SOLD_OUT -> hasActiveStock.not()
        }
    }

    private fun orderBy(
        sort: SellerProductSort,
        effectivePrice: NumberExpression<Long>,
    ): Array<OrderSpecifier<*>> =
        when (sort) {
            SellerProductSort.LATEST -> arrayOf(product.id.desc())
            SellerProductSort.PRICE_ASC -> arrayOf(effectivePrice.asc(), product.id.desc())
            SellerProductSort.PRICE_DESC -> arrayOf(effectivePrice.desc(), product.id.desc())
        }
}
