package com.aechak.infra.persistence.order

import com.aechak.application.order.port.OrderCatalogQueryPort
import com.aechak.application.order.port.view.OrderCatalogItemView
import com.aechak.domain.product.option.QOptionCombination
import com.aechak.domain.product.product.QProduct
import com.aechak.domain.product.version.QProductVersion
import com.aechak.domain.seller.seller.QSeller
import com.querydsl.core.types.Expression
import com.querydsl.core.types.Projections
import com.querydsl.jpa.JPAExpressions
import com.querydsl.jpa.impl.JPAQueryFactory
import org.springframework.stereotype.Repository

private val optionCombination = QOptionCombination.optionCombination
private val product = QProduct.product
private val seller = QSeller.seller
private val productVersion = QProductVersion.productVersion

/**
 * 주문 BC가 상품과 셀러 테이블을 직접 조인함. 공유 DB라 성립하는 방식이고 격리가 아니라 관리된 결합임.
 * 조인은 이 파일 밖으로 번지지 않게 하고 서비스는 포트로만 접근함.
 */
@Repository
class OrderCatalogQueryAdapter(
    private val queryFactory: JPAQueryFactory,
) : OrderCatalogQueryPort {
    override fun findItems(optionCombinationIds: Collection<Long>): List<OrderCatalogItemView> {
        if (optionCombinationIds.isEmpty()) return emptyList()

        return queryFactory
            .select(itemProjection())
            .from(optionCombination)
            .join(optionCombination.product, product)
            .join(seller)
            .on(seller.userId.eq(product.sellerId))
            .where(optionCombination.id.`in`(optionCombinationIds))
            .fetch()
    }

    private fun itemProjection(): Expression<OrderCatalogItemView> =
        Projections.constructor(
            OrderCatalogItemView::class.java,
            optionCombination.id,
            product.id,
            latestProductVersionId(),
            optionCombination.stockQuantity,
            optionCombination.isActive,
            product.saleStatus,
            seller.status,
            product.inspectionStatus,
            optionCombination.additionalPrice,
            product.regularPrice,
            product.discountPrice,
            product.discountStartAt,
            product.discountEndAt,
            seller.userId,
            seller.storeName,
            seller.baseShippingFee,
            seller.freeShippingThreshold,
        )

    // append-only라 상품별 id 최대가 최신 버전(version_no 최대와 동치)
    private fun latestProductVersionId(): Expression<Long> =
        JPAExpressions
            .select(productVersion.id.max())
            .from(productVersion)
            .where(productVersion.product.id.eq(product.id))
}
