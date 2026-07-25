package com.aechak.infra.persistence.product

import com.aechak.application.product.port.ProductCatalogCondition
import com.aechak.application.product.port.ProductCatalogQueryPort
import com.aechak.application.product.port.ProductCatalogSort
import com.aechak.application.product.port.result.ProductCatalogView
import com.aechak.domain.product.category.QCategory
import com.aechak.domain.product.category.enums.CategoryStatus
import com.aechak.domain.product.product.QProduct
import com.aechak.domain.product.product.enums.InspectionStatus
import com.aechak.domain.product.product.enums.SaleStatus
import com.aechak.domain.seller.seller.QSeller
import com.aechak.domain.seller.seller.enums.SellerStatus
import com.querydsl.core.BooleanBuilder
import com.querydsl.core.types.OrderSpecifier
import com.querydsl.core.types.Predicate
import com.querydsl.core.types.Projections
import com.querydsl.core.types.dsl.CaseBuilder
import com.querydsl.core.types.dsl.NumberExpression
import com.querydsl.jpa.impl.JPAQueryFactory
import org.springframework.stereotype.Repository
import java.time.LocalDateTime

private val product = QProduct.product
private val category = QCategory.category
private val parent = QCategory("parent") // 중분류 서브트리 필터, 부모 카테고리 status 검증용 별칭
private val grandParent = QCategory("grandParent") // 조부모(대분류) status 검증용 별칭
private val seller = QSeller.seller

/**
 * application이 요청한 공개 상품 카탈로그 조회를 QueryDSL로 수행한다.
 * 상품·카테고리·셀러 정보를 조합해 노출 가능한 목록 view와 총개수, 커서 해석용 내부 id를 반환한다.
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
        return queryFactory
            .select(
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
                ),
            ).from(product)
            .join(seller)
            .on(seller.userId.eq(product.sellerId).and(seller.status.eq(SellerStatus.ACTIVE)))
            .leftJoin(product.category, category)
            .leftJoin(category.parent, parent)
            .leftJoin(parent.parent, grandParent)
            .where(
                visible(),
                categoryActive(),
                categoryFilter(condition.categoryId),
                keyset(condition, effectivePrice),
            ).orderBy(*orderBy(condition.sort, effectivePrice))
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
        queryFactory
            .select(product.count())
            .from(product)
            .join(seller)
            .on(seller.userId.eq(product.sellerId).and(seller.status.eq(SellerStatus.ACTIVE)))
            .leftJoin(product.category, category)
            .leftJoin(category.parent, parent)
            .leftJoin(parent.parent, grandParent)
            .where(visible(), categoryActive(), categoryFilter(categoryId))
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

    /**
     * 주어진 시각의 유효가격을 계산하는 SQL 표현식을 반환한다.
     * 할인 시작, 종료 경계를 포함한 할인 기간이면 할인가, 그 외에는 정가를 선택한다.
     */
    private fun effectivePrice(now: LocalDateTime): NumberExpression<Long> =
        CaseBuilder()
            .`when`(
                product.discountPrice.isNotNull
                    .and(product.discountStartAt.isNull.or(product.discountStartAt.loe(now)))
                    .and(product.discountEndAt.isNull.or(product.discountEndAt.goe(now))),
            ).then(product.discountPrice)
            .otherwise(product.regularPrice)

    /**
     * 상품 자체가 공개 목록에 노출될 수 있는지 판정하는 조건을 반환한다.
     * 검수 승인 상태이고 판매 중 또는 품절인 상품만 통과한다. 셀러 ACTIVE 조건은 셀러 조인에서 적용한다.
     */
    private fun visible(): Predicate =
        BooleanBuilder()
            .and(product.inspectionStatus.eq(InspectionStatus.APPROVED))
            .and(product.saleStatus.`in`(SaleStatus.ON_SALE, SaleStatus.OUT_OF_STOCK))

    /**
     * 상품이 속한 카테고리 계층이 공개 목록에 노출 가능한지 판정하는 조건을 반환한다.
     * 상품 카테고리와 존재하는 모든 상위 카테고리가 ACTIVE인 경우만 통과한다.
     */
    private fun categoryActive(): Predicate =
        BooleanBuilder()
            .and(category.status.eq(CategoryStatus.ACTIVE))
            .and(parent.id.isNull.or(parent.status.eq(CategoryStatus.ACTIVE)))
            .and(grandParent.id.isNull.or(grandParent.status.eq(CategoryStatus.ACTIVE)))

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
