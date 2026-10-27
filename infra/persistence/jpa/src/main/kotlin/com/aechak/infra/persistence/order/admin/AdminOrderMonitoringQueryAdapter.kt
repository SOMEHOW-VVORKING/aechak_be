package com.aechak.infra.persistence.order.admin

import com.aechak.application.order.admin.port.AdminOrderMonitorCondition
import com.aechak.application.order.admin.port.AdminOrderMonitoringQueryPort
import com.aechak.application.order.admin.port.view.AdminOrderClaimView
import com.aechak.application.order.admin.port.view.AdminOrderLineView
import com.aechak.application.order.admin.port.view.AdminOrderShipmentView
import com.aechak.application.order.admin.port.view.AdminOrderStatusHistoryView
import com.aechak.application.order.admin.port.view.AdminOrderSummaryView
import com.aechak.application.order.admin.support.OrderDelayDeadlines
import com.aechak.domain.order.claim.QClaim
import com.aechak.domain.order.claim.enums.ClaimStatus
import com.aechak.domain.order.group.QOrderGroup
import com.aechak.domain.order.order.QOrder
import com.aechak.domain.order.order.QOrderItem
import com.aechak.domain.order.order.QOrderStatusHistory
import com.aechak.domain.order.order.enums.OrderStatus
import com.aechak.domain.order.shipment.QCourierMaster
import com.aechak.domain.order.shipment.QShipment
import com.aechak.domain.product.product.QProduct
import com.aechak.domain.product.version.QProductVersion
import com.querydsl.core.types.Expression
import com.querydsl.core.types.Predicate
import com.querydsl.core.types.Projections
import com.querydsl.jpa.JPAExpressions
import com.querydsl.jpa.impl.JPAQueryFactory
import org.springframework.stereotype.Repository
import java.time.LocalDateTime

private val order = QOrder.order
private val orderGroup = QOrderGroup.orderGroup
private val orderItem = QOrderItem.orderItem
private val productVersion = QProductVersion.productVersion
private val product = QProduct.product
private val shipment = QShipment.shipment
private val courier = QCourierMaster.courierMaster
private val claim = QClaim.claim
private val statusHistory = QOrderStatusHistory.orderStatusHistory

/** 종결 시각이 찍히지 않더라도 더 진행되지 않는 상태 — 진행 중 판정에서 뺀다. */
private val CLOSED_CLAIM_STATUSES = setOf(ClaimStatus.REJECTED, ClaimStatus.WITHDRAWN)

/**
 * 운영자용 전체 주문 조회. 셀러 스코프가 없어 모수가 전체 주문이므로 정렬·커서·기간 필터·지연 판정을
 * 전부 `orders` 한 테이블의 컬럼으로 끝낸다 — 주문그룹 조인은 구매자 식별자 하나 때문이고
 * 주문그룹 PK 등가 조인이라 모수를 늘리지 않는다.
 *
 * 주문 시각으로 `orders.created_at`을 쓴다. 주문그룹과 같은 트랜잭션에서 persist되어 사실상 같은
 * 시각인데, 같은 테이블에 있어 정렬 인덱스가 걸린다(V53의 `idx_orders_created_at_id` 등).
 */
@Repository
class AdminOrderMonitoringQueryAdapter(
    private val queryFactory: JPAQueryFactory,
) : AdminOrderMonitoringQueryPort {
    override fun findPage(condition: AdminOrderMonitorCondition): List<AdminOrderSummaryView> =
        queryFactory
            .select(summaryProjection())
            .from(order)
            .join(order.orderGroup, orderGroup)
            .where(
                order.status.`in`(condition.statuses),
                sellerMatches(condition.sellerId),
                keywordMatches(condition.keyword),
                orderedFrom(condition.orderedFrom),
                orderedBefore(condition.orderedToExclusive),
                delayed(condition.delayedOnly, condition.deadlines),
                afterCursor(condition),
            ).orderBy(order.createdAt.desc(), order.id.desc())
            .limit(condition.limit.toLong())
            .fetch()

    override fun findOne(orderPublicId: String): AdminOrderSummaryView? =
        queryFactory
            .select(summaryProjection())
            .from(order)
            .join(order.orderGroup, orderGroup)
            .where(
                order.publicId.eq(orderPublicId),
                order.status.ne(OrderStatus.PENDING_PAYMENT),
            ).fetchOne()

    override fun findLines(orderIds: Collection<Long>): List<AdminOrderLineView> {
        if (orderIds.isEmpty()) return emptyList()
        return queryFactory
            .select(
                Projections.constructor(
                    AdminOrderLineView::class.java,
                    order.id,
                    orderItem.id,
                    product.publicId,
                    productVersion.nameSnapshot,
                    orderItem.optionNameSnapshot,
                    orderItem.quantity,
                    orderItem.unitPriceSnapshot,
                    orderItem.discountAllocatedAmount,
                    orderItem.itemStatus,
                ),
            ).from(order)
            .join(order._items, orderItem)
            .join(productVersion)
            .on(productVersion.id.eq(orderItem.productVersionId))
            .join(productVersion.product, product)
            .where(order.id.`in`(orderIds))
            .orderBy(order.id.asc(), orderItem.id.asc())
            .fetch()
    }

    override fun findShipments(orderIds: Collection<Long>): List<AdminOrderShipmentView> {
        if (orderIds.isEmpty()) return emptyList()
        return queryFactory
            .select(
                Projections.constructor(
                    AdminOrderShipmentView::class.java,
                    shipment.order.id,
                    shipment.id,
                    courier.courierCode,
                    courier.courierName,
                    shipment.trackingNumber,
                    shipment.dispatchedAt,
                    shipment.deliveredAt,
                    shipment.lastTrackingStatus,
                ),
            ).from(shipment)
            .join(shipment.courier, courier)
            .where(shipment.order.id.`in`(orderIds))
            .orderBy(shipment.order.id.asc(), shipment.id.asc())
            .fetch()
    }

    override fun findOpenClaims(orderIds: Collection<Long>): List<AdminOrderClaimView> {
        if (orderIds.isEmpty()) return emptyList()
        return queryFactory
            .select(
                Projections.constructor(
                    AdminOrderClaimView::class.java,
                    claim.orderId,
                    claim.id,
                    claim.publicId,
                    claim.claimType,
                    claim.status,
                ),
            ).from(claim)
            .where(
                claim.orderId.`in`(orderIds),
                claim.resolvedAt.isNull,
                claim.status.notIn(CLOSED_CLAIM_STATUSES),
            ).orderBy(claim.orderId.asc(), claim.id.asc())
            .fetch()
    }

    override fun findStatusHistories(orderId: Long): List<AdminOrderStatusHistoryView> =
        queryFactory
            .select(
                Projections.constructor(
                    AdminOrderStatusHistoryView::class.java,
                    statusHistory.id,
                    statusHistory.fromStatus,
                    statusHistory.toStatus,
                    statusHistory.triggeredBy,
                    statusHistory.createdAt,
                ),
            ).from(statusHistory)
            .where(statusHistory.order.id.eq(orderId))
            .orderBy(statusHistory.createdAt.asc(), statusHistory.id.asc())
            .fetch()

    private fun summaryProjection(): Expression<AdminOrderSummaryView> =
        Projections.constructor(
            AdminOrderSummaryView::class.java,
            order.id,
            order.publicId,
            orderGroup.publicId,
            order.status,
            order.createdAt,
            order.sellerId,
            order.sellerNameSnapshot,
            orderGroup.buyerId,
            order.sellerShippingFee,
            order.allocatedCouponDiscount,
            order.autoConfirmDueAt,
            order.purchaseConfirmedAt,
            order.confirmType,
        )

    private fun sellerMatches(sellerId: Long?): Predicate? = sellerId?.let { order.sellerId.eq(it) }

    /**
     * 주문번호·셀러 상호·상품명·옵션명으로 검색한다. 구매자는 검색 대상이 아니다 —
     * 닉네임이 user BC 테이블에 있어 이 포트가 조인할 수 없고, 주문에 남은 수령인명은 암호문이라 LIKE가 불가능하다.
     */
    private fun keywordMatches(keyword: String?): Predicate? =
        keyword?.let {
            order.publicId
                .containsIgnoreCase(it)
                .or(order.sellerNameSnapshot.containsIgnoreCase(it))
                .or(hasItemMatching(it))
        }

    private fun hasItemMatching(keyword: String): Predicate {
        val keywordOrder = QOrder("keywordOrder")
        val keywordItem = QOrderItem("keywordItem")
        val keywordVersion = QProductVersion("keywordVersion")
        return JPAExpressions
            .selectOne()
            .from(keywordOrder)
            .join(keywordOrder._items, keywordItem)
            .join(keywordVersion)
            .on(keywordVersion.id.eq(keywordItem.productVersionId))
            .where(
                keywordOrder.id.eq(order.id),
                keywordVersion.nameSnapshot
                    .containsIgnoreCase(keyword)
                    .or(keywordItem.optionNameSnapshot.containsIgnoreCase(keyword)),
            ).exists()
    }

    /** OrderDelayDeadlines.reasonFor와 같은 두 축이다 — 한쪽만 고치면 카드 건수와 목록 신호가 어긋난다 */
    private fun delayed(
        delayedOnly: Boolean,
        deadlines: OrderDelayDeadlines,
    ): Predicate? = if (delayedOnly) delayedPredicate(order, deadlines) else null

    /** 주문 시각 내림차순의 다음 페이지 — 같은 시각이면 주문 id로 가른다 */
    private fun afterCursor(condition: AdminOrderMonitorCondition): Predicate? =
        condition.cursor?.let {
            order.createdAt
                .lt(it.lastOrderedAt)
                .or(order.createdAt.eq(it.lastOrderedAt).and(order.id.lt(it.lastOrderId)))
        }

    private fun orderedFrom(from: LocalDateTime?): Predicate? = from?.let { order.createdAt.goe(it) }

    private fun orderedBefore(toExclusive: LocalDateTime?): Predicate? = toExclusive?.let { order.createdAt.lt(it) }
}
