package com.aechak.infra.persistence.order.cart

import com.aechak.application.order.cart.port.CartCatalogQueryPort
import com.aechak.application.order.cart.port.view.CartCatalogItemView
import com.aechak.domain.product.option.QOptionCombination
import com.aechak.domain.product.product.QProduct
import com.aechak.domain.seller.seller.QSeller
import com.querydsl.core.types.Expression
import com.querydsl.core.types.Projections
import com.querydsl.jpa.impl.JPAQueryFactory
import org.springframework.stereotype.Repository

private val optionCombination = QOptionCombination.optionCombination
private val product = QProduct.product
private val seller = QSeller.seller

/**
 * 주문 BC가 상품과 셀러 테이블을 직접 조인함. 공유 DB라 성립하는 방식이고 격리가 아니라 관리된 결합임.
 * 조인은 이 파일 밖으로 번지지 않게 하고 서비스는 포트로만 접근함.
 * 서버 분리 시 바꿀 곳도 여기 하나. 조인을 API 호출이나 로컬 복제본 조회로 갈아끼우면 됨.
 */
@Repository
class CartCatalogQueryAdapter(
    private val queryFactory: JPAQueryFactory,
) : CartCatalogQueryPort {
    override fun findItem(optionCombinationId: Long): CartCatalogItemView? =
        queryFactory
            .select(itemProjection())
            .from(optionCombination)
            .join(optionCombination.product, product)
            .join(seller)
            .on(seller.userId.eq(product.sellerId))
            .where(optionCombination.id.eq(optionCombinationId))
            .fetchOne()

    override fun findItems(optionCombinationIds: Collection<Long>): List<CartCatalogItemView> {
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

    private fun itemProjection(): Expression<CartCatalogItemView> =
        Projections.constructor(
            CartCatalogItemView::class.java,
            optionCombination.id,
            product.publicId,
            optionCombination.stockQuantity,
            optionCombination.isActive,
            product.saleStatus,
            seller.status,
            optionCombination.name,
            optionCombination.additionalPrice,
            product.name,
            product.representativeImageKey,
            product.regularPrice,
            product.discountPrice,
            product.discountStartAt,
            product.discountEndAt,
            seller.userId,
            seller.storeName,
            seller.baseShippingFee,
            seller.freeShippingThreshold,
        )
}
