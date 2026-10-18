package com.aechak.infra.persistence.order

import com.aechak.application.order.seller.port.SellerOrderListCondition
import com.aechak.application.order.seller.port.SellerOrderQueryPort
import com.aechak.application.order.seller.port.view.SellerOrderClaimView
import com.aechak.application.order.seller.port.view.SellerOrderLineView
import com.aechak.application.order.seller.port.view.SellerOrderShipmentView
import com.aechak.application.order.seller.port.view.SellerOrderSummaryView
import com.aechak.domain.order.claim.QClaim
import com.aechak.domain.order.claim.enums.ClaimStatus
import com.aechak.domain.order.group.QOrderGroup
import com.aechak.domain.order.order.QOrder
import com.aechak.domain.order.order.QOrderItem
import com.aechak.domain.order.order.enums.OrderStatus
import com.aechak.domain.order.shipment.QCourierMaster
import com.aechak.domain.order.shipment.QShipment
import com.aechak.domain.product.product.QProduct
import com.aechak.domain.product.version.QProductVersion
import com.querydsl.core.types.Expression
import com.querydsl.core.types.Predicate
import com.querydsl.core.types.Projections
import com.querydsl.core.types.dsl.Expressions
import com.querydsl.jpa.JPAExpressions
import com.querydsl.jpa.impl.JPAQuery
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

/** 종결 시각이 찍히지 않더라도 더 진행되지 않는 상태 — 진행 중 판정에서 뺀다. */
private val CLOSED_CLAIM_STATUSES = setOf(ClaimStatus.REJECTED, ClaimStatus.WITHDRAWN)

/**
 * 셀러 자신의 주문 목록·상세 조회를 QueryDSL로 수행한다.
 * 구매자 목록이 주문그룹 단위인 것과 달리 셀러 주문(orders) 행 단위이고, 금액도 그 셀러 몫만 집계한다.
 */
@Repository
class SellerOrderQueryAdapter(
    private val queryFactory: JPAQueryFactory,
) : SellerOrderQueryPort {
    override fun findPage(condition: SellerOrderListCondition): List<SellerOrderSummaryView> =
        baseQuery(condition, summaryProjection())
            .orderBy(order.id.desc())
            .offset(condition.offset)
            .limit(condition.limit.toLong())
            .fetch()

    override fun count(condition: SellerOrderListCondition): Long = baseQuery(condition, order.count()).fetchOne() ?: 0L

    override fun findOwned(
        orderPublicId: String,
        sellerId: Long,
    ): SellerOrderSummaryView? =
        queryFactory
            .select(summaryProjection())
            .from(order)
            .join(order.orderGroup, orderGroup)
            .where(
                order.publicId.eq(orderPublicId),
                order.sellerId.eq(sellerId),
                order.status.ne(OrderStatus.PENDING_PAYMENT),
            ).fetchOne()

    override fun findLines(orderIds: Collection<Long>): List<SellerOrderLineView> {
        if (orderIds.isEmpty()) return emptyList()
        return queryFactory
            .select(
                Projections.constructor(
                    SellerOrderLineView::class.java,
                    order.id,
                    orderItem.id,
                    product.publicId,
                    productVersion.nameSnapshot,
                    productVersion.thumbnailKeySnapshot,
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

    override fun findShipments(orderIds: Collection<Long>): List<SellerOrderShipmentView> {
        if (orderIds.isEmpty()) return emptyList()
        return queryFactory
            .select(
                Projections.constructor(
                    SellerOrderShipmentView::class.java,
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

    override fun findOpenClaims(orderIds: Collection<Long>): List<SellerOrderClaimView> {
        if (orderIds.isEmpty()) return emptyList()
        return queryFactory
            .select(
                Projections.constructor(
                    SellerOrderClaimView::class.java,
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

    /** 목록·카운트가 공유하는 조인·필터 골격. 주문그룹 조인은 주문일 필터와 배송지 스냅샷에 쓴다 */
    private fun <T> baseQuery(
        condition: SellerOrderListCondition,
        select: Expression<T>,
    ): JPAQuery<T> =
        queryFactory
            .select(select)
            .from(order)
            .join(order.orderGroup, orderGroup)
            .where(
                order.sellerId.eq(condition.sellerId),
                order.status.`in`(condition.statuses),
                keywordMatches(condition.keyword),
                orderedFrom(condition.orderedFrom),
                orderedBefore(condition.orderedToExclusive),
            )

    private fun summaryProjection(): Expression<SellerOrderSummaryView> =
        Projections.constructor(
            SellerOrderSummaryView::class.java,
            order.id,
            order.publicId,
            orderGroup.publicId,
            order.status,
            orderGroup.createdAt,
            orderGroup.buyerId,
            orderGroup.deliveryAddress.receiverNameEnc,
            orderGroup.deliveryAddress.contactNumberEnc,
            orderGroup.deliveryAddress.zipCode,
            orderGroup.deliveryAddress.baseAddress,
            orderGroup.deliveryAddress.detailAddress,
            orderGroup.deliveryAddress.deliveryMemo,
            itemCount(),
            productAmount(),
            order.sellerShippingFee,
            order.allocatedCouponDiscount,
            order.autoConfirmDueAt,
        )

    /** 품목 수 — 행마다 품목을 다시 조회하지 않게 쿼리에서 센다 */
    private fun itemCount(): Expression<Long> {
        val countOrder = QOrder("countOrder")
        val countItem = QOrderItem("countItem")
        return JPAExpressions
            .select(countItem.count())
            .from(countOrder)
            .join(countOrder._items, countItem)
            .where(countOrder.id.eq(order.id))
    }

    /** 이 셀러 몫 상품금액 — 주문 시점 단가 스냅샷 × 수량의 합 */
    private fun productAmount(): Expression<Long> {
        val amountOrder = QOrder("amountOrder")
        val amountItem = QOrderItem("amountItem")
        return JPAExpressions
            .select(
                Expressions.numberTemplate(
                    Long::class.javaObjectType,
                    "coalesce(sum({0} * {1}), 0)",
                    amountItem.unitPriceSnapshot,
                    amountItem.quantity,
                ),
            ).from(amountOrder)
            .join(amountOrder._items, amountItem)
            .where(amountOrder.id.eq(order.id))
    }

    /** 수령인명·연락처는 암호문이라 LIKE가 불가능하다 — 주문번호·상품명·옵션명만 검색한다 */
    private fun keywordMatches(keyword: String?): Predicate? =
        keyword?.let {
            order.publicId.containsIgnoreCase(it).or(hasItemMatching(it))
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

    private fun orderedFrom(from: LocalDateTime?): Predicate? = from?.let { orderGroup.createdAt.goe(it) }

    private fun orderedBefore(toExclusive: LocalDateTime?): Predicate? = toExclusive?.let { orderGroup.createdAt.lt(it) }
}
