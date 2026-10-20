package com.aechak.infra.persistence.order.claim

import com.aechak.application.order.claim.port.ClaimItemProductQueryPort
import com.aechak.application.order.claim.port.view.ClaimItemProductView
import com.aechak.domain.order.order.QOrderItem
import com.aechak.domain.product.version.QProductVersion
import com.querydsl.core.types.Projections
import com.querydsl.jpa.impl.JPAQueryFactory
import org.springframework.stereotype.Repository

private val orderItem = QOrderItem.orderItem
private val productVersion = QProductVersion.productVersion

@Repository
class ClaimItemProductQueryAdapter(
    private val queryFactory: JPAQueryFactory,
) : ClaimItemProductQueryPort {
    override fun findProductNames(orderItemIds: Collection<Long>): List<ClaimItemProductView> {
        if (orderItemIds.isEmpty()) return emptyList()
        return queryFactory
            .select(
                Projections.constructor(
                    ClaimItemProductView::class.java,
                    orderItem.id,
                    productVersion.nameSnapshot,
                ),
            ).from(orderItem)
            .join(productVersion)
            .on(productVersion.id.eq(orderItem.productVersionId))
            .where(orderItem.id.`in`(orderItemIds))
            .fetch()
    }
}
