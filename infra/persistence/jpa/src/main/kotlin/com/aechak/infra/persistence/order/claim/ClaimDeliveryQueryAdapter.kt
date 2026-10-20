package com.aechak.infra.persistence.order.claim

import com.aechak.application.order.claim.port.ClaimDeliveryQueryPort
import com.aechak.domain.order.order.QOrderStatusHistory
import com.aechak.domain.order.order.enums.OrderStatus
import com.querydsl.jpa.impl.JPAQueryFactory
import org.springframework.stereotype.Repository
import java.time.LocalDateTime

private val history = QOrderStatusHistory.orderStatusHistory

@Repository
class ClaimDeliveryQueryAdapter(
    private val queryFactory: JPAQueryFactory,
) : ClaimDeliveryQueryPort {
    /** 전이 이력은 전이와 같은 트랜잭션에서 쌓이므로 created_at이 전이 시각이다. */
    override fun findDeliveredAt(orderId: Long): LocalDateTime? =
        queryFactory
            .select(history.createdAt.max())
            .from(history)
            .where(history.order.id.eq(orderId), history.toStatus.eq(OrderStatus.DELIVERED))
            .fetchOne()
}
