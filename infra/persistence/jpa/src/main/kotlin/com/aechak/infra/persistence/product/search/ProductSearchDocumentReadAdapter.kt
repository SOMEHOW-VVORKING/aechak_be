package com.aechak.infra.persistence.product.search

import com.aechak.application.product.search.port.ProductSearchDocument
import com.aechak.application.product.search.port.ProductSearchDocumentReader
import com.aechak.infra.persistence.product.product
import com.aechak.infra.persistence.product.productStats
import com.aechak.infra.persistence.product.seller
import com.querydsl.core.types.Projections
import com.querydsl.jpa.impl.JPAQueryFactory
import org.springframework.stereotype.Repository

/** 검색 색인 원본. 상품 전체를 id 순서로 읽고 셀러와 상품 통계는 왼쪽 조인으로 붙임 */
@Repository
class ProductSearchDocumentReadAdapter(
    private val queryFactory: JPAQueryFactory,
) : ProductSearchDocumentReader {
    override fun readAfter(
        afterId: Long,
        size: Int,
    ): List<ProductSearchDocument> =
        queryFactory
            .select(
                Projections.constructor(
                    ProductSearchDocument::class.java,
                    product.id,
                    product.publicId,
                    product.name,
                    product.category.id,
                    product.sellerId,
                    product.inspectionStatus,
                    product.saleStatus,
                    product.regularPrice,
                    product.discountPrice,
                    product.discountStartAt,
                    product.discountEndAt,
                    product.reviewCount,
                    product.representativeImageKey,
                    seller.status,
                    seller.baseShippingFee,
                    seller.storeName,
                    productStats.reviewCount,
                    productStats.averageRating,
                ),
            ).from(product)
            .leftJoin(seller)
            .on(seller.userId.eq(product.sellerId))
            .leftJoin(productStats)
            .on(productStats.productId.eq(product.id))
            .where(product.id.gt(afterId))
            .orderBy(product.id.asc())
            .limit(size.toLong())
            .fetch()
}
