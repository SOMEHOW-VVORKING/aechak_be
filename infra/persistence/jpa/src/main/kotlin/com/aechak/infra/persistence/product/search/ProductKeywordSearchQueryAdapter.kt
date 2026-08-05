package com.aechak.infra.persistence.product.search

import com.aechak.application.product.port.view.ProductCatalogView
import com.aechak.application.product.search.port.ProductKeywordSearchCondition
import com.aechak.application.product.search.port.ProductKeywordSearchPort
import com.aechak.infra.persistence.product.catalogViewProjection
import com.aechak.infra.persistence.product.effectivePrice
import com.aechak.infra.persistence.product.product
import com.aechak.infra.persistence.product.visibleProductQuery
import com.querydsl.core.types.Predicate
import com.querydsl.jpa.impl.JPAQueryFactory
import org.springframework.stereotype.Repository

@Repository
class ProductKeywordSearchQueryAdapter(
    private val queryFactory: JPAQueryFactory,
) : ProductKeywordSearchPort {
    override fun search(condition: ProductKeywordSearchCondition): List<ProductCatalogView> =
        visibleProductQuery(queryFactory, catalogViewProjection(effectivePrice(condition.now)))
            .where(nameContains(condition.keyword), keyset(condition.lastId))
            .orderBy(product.id.desc())
            .limit(condition.limit.toLong())
            .fetch()

    override fun countMatching(keyword: String): Long =
        visibleProductQuery(queryFactory, product.count())
            .where(nameContains(keyword))
            .fetchOne() ?: 0L

    override fun findIdByPublicId(publicId: String): Long? =
        queryFactory
            .select(product.id)
            .from(product)
            .where(product.publicId.eq(publicId))
            .fetchOne()

    private fun nameContains(keyword: String): Predicate = product.name.containsIgnoreCase(keyword)

    private fun keyset(lastId: Long?): Predicate? = lastId?.let { product.id.lt(it) }
}
