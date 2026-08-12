package com.aechak.infra.persistence.product

import com.aechak.application.product.product.port.ProductDetailQueryPort
import com.aechak.application.product.product.port.view.ProductCatalogDetailView
import com.aechak.application.product.product.port.view.ProductImageView
import com.aechak.domain.product.product.QProductImage
import com.querydsl.core.types.Expression
import com.querydsl.core.types.Projections
import com.querydsl.jpa.impl.JPAQueryFactory
import org.springframework.stereotype.Repository

private val productImage = QProductImage.productImage

/** 공개 상품 상세 조회 */
@Repository
class ProductDetailQueryAdapter(
    private val queryFactory: JPAQueryFactory,
) : ProductDetailQueryPort {
    override fun findVisibleDetail(publicId: String): ProductCatalogDetailView? =
        visibleProductQuery(queryFactory, detailProjection())
            .where(product.publicId.eq(publicId))
            .fetchOne()

    /** 상품 이미지 전체를 sortOrder, id 순으로 반환 */
    override fun findImagesByProductId(productId: Long): List<ProductImageView> =
        queryFactory
            .select(
                Projections.constructor(
                    ProductImageView::class.java,
                    productImage.imageType,
                    productImage.storageKey,
                    productImage.sortOrder,
                ),
            ).from(product)
            .join(product._images, productImage)
            .where(product.id.eq(productId))
            .orderBy(productImage.sortOrder.asc(), productImage.id.asc())
            .fetch()

    private fun detailProjection(): Expression<ProductCatalogDetailView> =
        Projections.constructor(
            ProductCatalogDetailView::class.java,
            product.id,
            product.publicId,
            product.name,
            product.description,
            product.representativeImageKey,
            product.regularPrice,
            product.discountPrice,
            product.discountStartAt,
            product.discountEndAt,
            product.saleStatus,
            seller.storeName,
            seller.profileImageKey,
            seller.baseShippingFee,
            seller.freeShippingThreshold,
            category.id,
            category.name,
            category.depth,
            parent.id,
            parent.name,
            grandParent.id,
            grandParent.name,
        )
}
