package com.aechak.infra.persistence.product

import com.aechak.application.product.product.port.ProductCatalogCondition
import com.aechak.application.product.product.port.ProductCatalogQueryPort
import com.aechak.application.product.product.port.ProductCatalogSort
import com.aechak.application.product.product.port.view.ProductCatalogView
import com.querydsl.core.types.OrderSpecifier
import com.querydsl.core.types.Predicate
import com.querydsl.core.types.dsl.NumberExpression
import com.querydsl.jpa.impl.JPAQueryFactory
import org.springframework.stereotype.Repository

/**
 * application이 요청한 공개 상품 카탈로그 목록 조회를 QueryDSL로 수행한다.
 * 상품·카테고리·셀러 정보를 조합해 노출 가능한 목록 view와 총개수, 커서 해석용 내부 id를 반환한다.
 * 노출 조인·술어는 상세·옵션 어댑터와 [visibleProductQuery]로 공유한다.
 */
@Repository
class ProductCatalogQueryAdapter(
    private val queryFactory: JPAQueryFactory,
) : ProductCatalogQueryPort {
    /**
     * 공개 상품 목록에 노출할 수 있는 상품 한 페이지를 반환한다.
     *
     * 검수, 판매 상태가 노출 조건을 만족하고, 셀러와 카테고리 계층이 모두 ACTIVE인 상품만 조회한다.
     * 요청한 정렬, 중분류 필터, keyset 커서, 페이지 크기를 적용하고 셀러명과 정렬 기준 가격을 포함한 목록 전용 조회 결과를 돌려준다.
     */
    override fun findVisiblePage(condition: ProductCatalogCondition): List<ProductCatalogView> {
        val effectivePrice = effectivePrice(condition.now)
        return visibleProductQuery(queryFactory, catalogViewProjection(effectivePrice))
            .leftJoin(productStats)
            .on(productStats.productId.eq(product.id))
            .where(categoryFilter(condition.categoryId), keyset(condition, effectivePrice))
            .orderBy(*orderBy(condition.sort, effectivePrice))
            .limit(condition.limit.toLong())
            .fetch()
    }

    /**
     * 공개 상품 목록에 노출할 수 있는 상품의 총개수를 반환한다.
     *
     * [findVisiblePage]와 동일한 상품, 셀러, 카테고리 노출 조건과 중분류 필터를 반영한다.
     * 셀러가 비활성이거나 존재하지 않아 목록에서 제외되는 상품은 총개수에도 포함하지 않는다.
     */
    override fun countVisible(categoryId: Long?): Long =
        visibleProductQuery(queryFactory, product.count())
            .where(categoryFilter(categoryId))
            .fetchOne() ?: 0L

    /**
     * 외부에 노출된 상품 publicId를 커서 keyset에 사용할 내부 id로 변환한다.
     * 해당 publicId의 상품이 없으면 null을 반환한다.
     */
    override fun findIdByPublicId(publicId: String): Long? =
        queryFactory
            .select(product.id)
            .from(product)
            .where(product.publicId.eq(publicId))
            .fetchOne()

    override fun findVisibleIdByPublicId(publicId: String): Long? =
        visibleProductQuery(queryFactory, product.id)
            .where(product.publicId.eq(publicId))
            .fetchOne()

    /**
     * 중분류 필터가 있으면 해당 중분류와 그 아래 소분류의 상품만 조회하는 조건을 반환한다.
     * 필터가 없으면 null을 반환해 전체 카테고리를 조회한다.
     */
    private fun categoryFilter(categoryId: Long?): Predicate? = categoryId?.let { category.id.eq(it).or(parent.id.eq(it)) }

    /**
     * 커서가 가리킨 상품 다음부터 조회하는 keyset 경계 조건을 반환한다.
     * 커서가 없는 첫 페이지면 null을 반환하고 이후 페이지면 [orderBy]와 동일한 가격, id 순서의 다음 범위를 선택한다.
     */
    private fun keyset(
        condition: ProductCatalogCondition,
        effectivePrice: NumberExpression<Long>,
    ): Predicate? {
        val lastId = condition.lastId ?: return null
        return when (condition.sort) {
            ProductCatalogSort.LATEST -> {
                product.id.lt(lastId)
            }

            ProductCatalogSort.PRICE_ASC -> {
                val lastPrice = requireNotNull(condition.lastPrice)
                effectivePrice
                    .gt(lastPrice)
                    .or(effectivePrice.eq(lastPrice).and(product.id.lt(lastId)))
            }
        }
    }

    /**
     * 요청한 카탈로그 정렬 방식에 맞는 SQL 정렬 기준을 반환한다.
     * 최신순은 id 내림차순, 낮은 가격순은 유효가격 오름차순 후 id 내림차순을 적용한다.
     */
    private fun orderBy(
        sort: ProductCatalogSort,
        effectivePrice: NumberExpression<Long>,
    ): Array<OrderSpecifier<*>> =
        when (sort) {
            ProductCatalogSort.LATEST -> arrayOf(product.id.desc())
            ProductCatalogSort.PRICE_ASC -> arrayOf(effectivePrice.asc(), product.id.desc())
        }
}
