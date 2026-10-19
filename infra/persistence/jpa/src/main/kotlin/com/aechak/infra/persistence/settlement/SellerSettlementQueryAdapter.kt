package com.aechak.infra.persistence.settlement

import com.aechak.application.settlement.port.SellerSettlementCondition
import com.aechak.application.settlement.port.SellerSettlementQueryPort
import com.aechak.application.settlement.port.SellerSettlementStatus
import com.aechak.application.settlement.port.view.SellerSettlementItemView
import com.aechak.application.settlement.port.view.SellerSettlementOrderView
import com.aechak.domain.order.order.QOrder
import com.aechak.domain.order.order.QOrderItem
import com.aechak.domain.product.version.QProductVersion
import com.aechak.domain.settlement.settlement.QSettlement
import com.aechak.domain.settlement.settlement.enums.SettlementStatus
import com.querydsl.core.types.Expression
import com.querydsl.core.types.Predicate
import com.querydsl.core.types.Projections
import com.querydsl.core.types.dsl.CaseBuilder
import com.querydsl.core.types.dsl.Expressions
import com.querydsl.core.types.dsl.NumberExpression
import com.querydsl.core.types.dsl.NumberPath
import com.querydsl.jpa.JPAExpressions
import com.querydsl.jpa.impl.JPAQueryFactory
import org.springframework.stereotype.Repository

private val settlement = QSettlement.settlement
private val order = QOrder.order
private val orderItem = QOrderItem.orderItem
private val productVersion = QProductVersion.productVersion

/**
 * 셀러 정산 조회 어댑터. 정산 행은 주문품목 단위라 주문 단위 집계를 SQL에서 끝낸다 —
 * 목록·상세가 같은 집계식과 같은 제외 규칙(취소 행 제외)을 쓰므로 둘의 숫자가 갈릴 수 없다.
 * 금액은 전부 저장된 값의 합이고 요율이나 수수료를 다시 계산하지 않는다.
 */
@Repository
class SellerSettlementQueryAdapter(
    private val queryFactory: JPAQueryFactory,
) : SellerSettlementQueryPort {
    override fun findOrderPage(condition: SellerSettlementCondition): List<SellerSettlementOrderView> =
        queryFactory
            .select(orderProjection())
            .from(settlement)
            .join(order)
            .on(order.id.eq(settlement.orderId))
            .where(
                settlement.sellerId.eq(condition.sellerId),
                settlementMonthEq(settlement, condition.settlementMonth),
                notCancelled(settlement),
            ).groupBy(settlement.sellerId, settlement.orderId, order.publicId, order.purchaseConfirmedAt)
            .having(statusRankEq(settlement, condition.status))
            .orderBy(order.purchaseConfirmedAt.desc(), settlement.orderId.desc())
            .offset(condition.offset)
            .limit(condition.limit.toLong())
            .fetch()

    /**
     * 주문 수. 접힌 상태로 걸러야 해서 상태 필터만 묶음 조건(GROUP BY + HAVING)을 서브쿼리로 돌린다 —
     * 정산 행을 그냥 걸러 세면 "일부 품목만 지급 완료"인 주문이 완료로도 대기로도 잡힌다.
     */
    override fun countOrders(condition: SellerSettlementCondition): Long =
        queryFactory
            .select(settlement.orderId.countDistinct())
            .from(settlement)
            .where(
                settlement.sellerId.eq(condition.sellerId),
                settlementMonthEq(settlement, condition.settlementMonth),
                notCancelled(settlement),
                orderIdInStatus(condition),
            ).fetchOne() ?: 0L

    override fun findOrder(orderPublicId: String): SellerSettlementOrderView? =
        queryFactory
            .select(orderProjection())
            .from(settlement)
            .join(order)
            .on(order.id.eq(settlement.orderId))
            .where(order.publicId.eq(orderPublicId), notCancelled(settlement))
            .groupBy(settlement.sellerId, settlement.orderId, order.publicId, order.purchaseConfirmedAt)
            .fetchOne()

    /**
     * 품목 이름·수량은 외부 조인으로 읽는다 — 스냅샷 행이 없어도 금액 행은 남아야 한다.
     * 내부 조인이면 품목 하나가 통째로 빠져 내역의 합이 요약과 어긋난다.
     */
    override fun findItems(orderId: Long): List<SellerSettlementItemView> =
        queryFactory
            .select(
                Projections.constructor(
                    SellerSettlementItemView::class.java,
                    settlement.orderItemId,
                    productVersion.nameSnapshot,
                    orderItem.quantity,
                    settlement.grossAmount,
                    settlement.shippingFee,
                    settlement.commission,
                    settlement.pgFee,
                    settlement.sellerCouponBurden,
                    settlement.refundDeduction,
                    settlement.netAmount,
                ),
            ).from(settlement)
            .leftJoin(orderItem)
            .on(orderItem.id.eq(settlement.orderItemId))
            .leftJoin(productVersion)
            .on(productVersion.id.eq(orderItem.productVersionId))
            .where(settlement.orderId.eq(orderId), notCancelled(settlement))
            .orderBy(settlement.orderItemId.asc())
            .fetch()

    override fun findSettlementMonths(
        sellerId: Long,
        limit: Int,
    ): List<String> =
        queryFactory
            .select(settlement.settlementMonth)
            .distinct()
            .from(settlement)
            .where(settlement.sellerId.eq(sellerId), notCancelled(settlement))
            .orderBy(settlement.settlementMonth.desc())
            .limit(limit.toLong())
            .fetch()

    private fun orderProjection(): Expression<SellerSettlementOrderView> =
        Projections.constructor(
            SellerSettlementOrderView::class.java,
            settlement.sellerId,
            settlement.orderId,
            order.publicId,
            settlement.settlementMonth.max(),
            order.purchaseConfirmedAt,
            settlement.expectedPayoutDate.max(),
            amountSum(settlement.grossAmount),
            amountSum(settlement.shippingFee),
            amountSum(settlement.commission),
            amountSum(settlement.pgFee),
            amountSum(settlement.sellerCouponBurden),
            amountSum(settlement.refundDeduction),
            amountSum(settlement.netAmount),
            settlement.platformFeeRate.max(),
            settlement.pgFeeRate.max(),
            statusRank(settlement).max(),
        )

    /** 합계 타입을 Long으로 못박는다 — 묶음에 행이 있으니 coalesce가 쓰이는 일은 없고, 드라이버가 돌려주는 수치 타입만 고정한다 */
    private fun amountSum(path: NumberPath<Long>): NumberExpression<Long> =
        Expressions.numberTemplate(Long::class.javaObjectType, "coalesce(sum({0}), 0)", path)

    /**
     * 정산 행 하나의 표시 상태 우선순위. 저장 상태와 지급 편성 여부로 판정한다 —
     * 지급 묶음에 들어갔으면 "정산 예정", 아직이면 "정산 대기"다.
     */
    private fun statusRank(alias: QSettlement): NumberExpression<Int> =
        CaseBuilder()
            .`when`(alias.status.eq(SettlementStatus.ON_HOLD))
            .then(SellerSettlementStatus.HELD.rank)
            .`when`(alias.status.eq(SettlementStatus.PENDING).and(alias.payout.isNull()))
            .then(SellerSettlementStatus.PENDING.rank)
            .`when`(alias.status.eq(SettlementStatus.PENDING))
            .then(SellerSettlementStatus.SCHEDULED.rank)
            .otherwise(SellerSettlementStatus.COMPLETED.rank)

    private fun statusRankEq(
        alias: QSettlement,
        status: SellerSettlementStatus?,
    ): Predicate? = status?.let { statusRank(alias).max().eq(it.rank) }

    private fun orderIdInStatus(condition: SellerSettlementCondition): Predicate? {
        val status = condition.status ?: return null
        val grouped = QSettlement("statusGrouped")
        return settlement.orderId.`in`(
            JPAExpressions
                .select(grouped.orderId)
                .from(grouped)
                .where(
                    grouped.sellerId.eq(condition.sellerId),
                    settlementMonthEq(grouped, condition.settlementMonth),
                    notCancelled(grouped),
                ).groupBy(grouped.orderId)
                .having(statusRankEq(grouped, status)),
        )
    }

    private fun settlementMonthEq(
        alias: QSettlement,
        settlementMonth: String?,
    ): Predicate? = settlementMonth?.let { alias.settlementMonth.eq(it) }

    /** 취소된 정산 행은 지급 대상이 아니라 집계에서 아예 뺀다 — 목록·상세·월 목록이 같은 규칙을 쓴다 */
    private fun notCancelled(alias: QSettlement): Predicate = alias.status.ne(SettlementStatus.CANCELLED)
}
