package com.aechak.infra.persistence.order.claim

import com.aechak.application.order.claim.port.SellerClaimCondition
import com.aechak.application.order.claim.port.SellerClaimQueryPort
import com.aechak.application.order.claim.port.view.SellerClaimItemView
import com.aechak.application.order.claim.port.view.SellerClaimTypeCountView
import com.aechak.application.order.claim.port.view.SellerClaimView
import com.aechak.domain.order.claim.QClaim
import com.aechak.domain.order.claim.QClaimItem
import com.aechak.domain.order.order.QOrder
import com.aechak.domain.order.order.QOrderItem
import com.aechak.domain.order.shipment.QShipment
import com.aechak.domain.order.shipment.enums.ShipmentType
import com.aechak.domain.product.version.QProductVersion
import com.querydsl.core.types.Expression
import com.querydsl.core.types.Predicate
import com.querydsl.core.types.Projections
import com.querydsl.jpa.impl.JPAQuery
import com.querydsl.jpa.impl.JPAQueryFactory
import org.springframework.stereotype.Repository
import java.time.LocalDateTime

private val claim = QClaim.claim
private val claimItem = QClaimItem.claimItem
private val order = QOrder.order
private val orderItem = QOrderItem.orderItem
private val productVersion = QProductVersion.productVersion
private val shipment = QShipment.shipment

/**
 * 셀러 클레임 목록·상세 조회. 셀러 범위는 claims.seller_id 비정규화 컬럼 하나로 걸러
 * claim_items → order_items → orders 2홉 조인을 타지 않는다(인덱스: seller_id, status, id).
 * 주문 조인은 공개 식별자와 주문그룹 식별자를 얻기 위한 것이고 범위 판정에는 쓰지 않는다.
 */
@Repository
class SellerClaimQueryAdapter(
    private val queryFactory: JPAQueryFactory,
) : SellerClaimQueryPort {
    override fun findPage(condition: SellerClaimCondition): List<SellerClaimView> =
        baseQuery(viewProjection())
            .where(
                claim.sellerId.eq(condition.sellerId),
                claimTypeIn(condition),
                statusIn(condition),
                refundStatusIn(condition),
                requestedFrom(condition.requestedFrom),
                requestedBefore(condition.requestedToExclusive),
            ).orderBy(claim.id.desc())
            .offset(condition.offset)
            .limit(condition.limit.toLong())
            .fetch()

    override fun count(condition: SellerClaimCondition): Long =
        queryFactory
            .select(claim.count())
            .from(claim)
            .where(
                claim.sellerId.eq(condition.sellerId),
                claimTypeIn(condition),
                statusIn(condition),
                refundStatusIn(condition),
                requestedFrom(condition.requestedFrom),
                requestedBefore(condition.requestedToExclusive),
            ).fetchOne() ?: 0L

    override fun countByClaimType(condition: SellerClaimCondition): List<SellerClaimTypeCountView> =
        queryFactory
            .select(Projections.constructor(SellerClaimTypeCountView::class.java, claim.claimType, claim.count()))
            .from(claim)
            .where(
                claim.sellerId.eq(condition.sellerId),
                statusIn(condition),
                refundStatusIn(condition),
                requestedFrom(condition.requestedFrom),
                requestedBefore(condition.requestedToExclusive),
            ).groupBy(claim.claimType)
            .fetch()

    override fun findOne(
        claimPublicId: String,
        sellerId: Long,
    ): SellerClaimView? =
        baseQuery(viewProjection())
            .where(claim.publicId.eq(claimPublicId), claim.sellerId.eq(sellerId))
            .fetchOne()

    override fun findItems(claimIds: Collection<Long>): List<SellerClaimItemView> {
        if (claimIds.isEmpty()) return emptyList()
        return queryFactory
            .select(
                Projections.constructor(
                    SellerClaimItemView::class.java,
                    claim.id,
                    orderItem.id,
                    productVersion.nameSnapshot,
                    orderItem.optionNameSnapshot,
                    claimItem.quantity,
                    orderItem.unitPriceSnapshot,
                    claimItem.refundAmount,
                    orderItem.itemStatus,
                ),
            ).from(claim)
            .join(claim._items, claimItem)
            .join(claimItem.orderItem, orderItem)
            .join(productVersion)
            .on(productVersion.id.eq(orderItem.productVersionId))
            .where(claim.id.`in`(claimIds))
            .orderBy(claim.id.desc(), claimItem.id.asc())
            .fetch()
    }

    override fun findDispatchedOrderIds(orderIds: Collection<Long>): List<Long> {
        if (orderIds.isEmpty()) return emptyList()
        return queryFactory
            .select(shipment.order.id)
            .distinct()
            .from(shipment)
            .where(
                shipment.order.id.`in`(orderIds),
                shipment.shipmentType.eq(ShipmentType.INITIAL),
                shipment.dispatchedAt.isNotNull,
            ).fetch()
    }

    private fun <T> baseQuery(select: Expression<T>): JPAQuery<T> =
        queryFactory
            .select(select)
            .from(claim)
            .join(order)
            .on(order.id.eq(claim.orderId))

    private fun viewProjection(): Expression<SellerClaimView> =
        Projections.constructor(
            SellerClaimView::class.java,
            claim.id,
            claim.publicId,
            claim.orderId,
            order.orderGroup.id,
            order.publicId,
            claim.claimType,
            claim.reasonCode,
            claim.reasonDetail,
            claim.status,
            claim.refundStatus,
            claim.sellerApprovalStatus,
            claim.settlementStatus,
            claim.returnCostBearer,
            claim.rejectionReason,
            claim.sellerMemo,
            claim.requestedAt,
            claim.autoApproveDueAt,
            claim.approvedAt,
            claim.collectionStartedAt,
            claim.clawbackDepositedAt,
            claim.resolvedAt,
        )

    private fun claimTypeIn(condition: SellerClaimCondition): Predicate? =
        condition.claimTypes.takeIf { it.isNotEmpty() }?.let { claim.claimType.`in`(it) }

    private fun statusIn(condition: SellerClaimCondition): Predicate? =
        condition.statuses.takeIf { it.isNotEmpty() }?.let { claim.status.`in`(it) }

    private fun refundStatusIn(condition: SellerClaimCondition): Predicate? =
        condition.refundStatuses.takeIf { it.isNotEmpty() }?.let { claim.refundStatus.`in`(it) }

    private fun requestedFrom(from: LocalDateTime?): Predicate? = from?.let { claim.requestedAt.goe(it) }

    private fun requestedBefore(toExclusive: LocalDateTime?): Predicate? = toExclusive?.let { claim.requestedAt.lt(it) }
}
