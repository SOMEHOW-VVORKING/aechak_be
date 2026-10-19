package com.aechak.application.order.seller.service

import com.aechak.application.order.seller.port.SellerOrderListCondition
import com.aechak.application.order.seller.port.SellerOrderQueryPort
import com.aechak.application.order.seller.port.view.SellerOrderClaimView
import com.aechak.application.order.seller.port.view.SellerOrderLineView
import com.aechak.application.order.seller.port.view.SellerOrderShipmentView
import com.aechak.application.order.seller.port.view.SellerOrderSummaryView
import com.aechak.application.order.seller.usecase.command.AcceptOrderCommand
import com.aechak.application.order.seller.usecase.query.SellerOrderSearchQuery
import com.aechak.application.pii.support.PiiStringCodec
import com.aechak.application.support.OffsetPageResult
import com.aechak.common.error.BusinessException
import com.aechak.domain.order.error.OrderErrorCode
import com.aechak.domain.order.order.OrderStatusHistory
import com.aechak.domain.order.order.enums.OrderStatus
import com.aechak.domain.order.order.enums.TriggeredBy
import com.aechak.domain.order.order.repository.OrderRepository
import com.aechak.domain.order.order.repository.OrderStatusHistoryRepository
import org.springframework.stereotype.Service
import java.time.LocalDate
import java.time.LocalDateTime

@Service
class SellerOrderService(
    private val orderRepository: OrderRepository,
    private val orderStatusHistoryRepository: OrderStatusHistoryRepository,
    private val sellerOrderQueryPort: SellerOrderQueryPort,
    private val piiStringCodec: PiiStringCodec,
) {
    fun acceptOrder(command: AcceptOrderCommand) {
        val order =
            orderRepository.findByPublicIdAndSellerId(command.orderPublicId, command.sellerId)
                ?: throw BusinessException(OrderErrorCode.ORDER_NOT_FOUND)

        // 전이의 심판은 조건부 UPDATE다. 경합에서 진 쪽은 0행을 받는다
        if (!orderRepository.transitionStatusIf(order.id, OrderStatus.PAID, OrderStatus.PREPARING)) {
            throw BusinessException(OrderErrorCode.ORDER_NOT_ACCEPTABLE)
        }
        order.accept()

        orderStatusHistoryRepository.save(
            OrderStatusHistory.record(
                order = order,
                fromStatus = OrderStatus.PAID,
                toStatus = order.status,
                triggeredBy = TriggeredBy.SELLER,
            ),
        )
    }

    fun getPage(query: SellerOrderSearchQuery): OffsetPageResult<SellerOrderSummaryView> {
        val condition = condition(query)
        return OffsetPageResult(
            items = sellerOrderQueryPort.findPage(condition),
            totalCount = sellerOrderQueryPort.count(condition),
            page = query.page,
            size = query.size,
        )
    }

    /** 주문 번호 존재 여부를 노출하지 않도록 타 셀러 주문도 ORDER_NOT_FOUND로 통일 */
    fun getOwned(
        sellerId: Long,
        orderPublicId: String,
    ): SellerOrderSummaryView =
        sellerOrderQueryPort.findOwned(orderPublicId, sellerId)
            ?: throw BusinessException(OrderErrorCode.ORDER_NOT_FOUND)

    fun getLines(orderIds: Collection<Long>): List<SellerOrderLineView> = sellerOrderQueryPort.findLines(orderIds)

    /** 주문당 최신 배송 한 건 — 교환 재발송이 붙으면 나중 행이 현재 배송이다 */
    fun getLatestShipments(orderIds: Collection<Long>): Map<Long, SellerOrderShipmentView> =
        sellerOrderQueryPort
            .findShipments(orderIds)
            .groupBy { it.orderId }
            .mapValues { (_, shipments) -> shipments.maxBy { it.shipmentId } }

    /** 주문당 최신 진행 클레임 한 건 */
    fun getOpenClaims(orderIds: Collection<Long>): Map<Long, SellerOrderClaimView> =
        sellerOrderQueryPort
            .findOpenClaims(orderIds)
            .groupBy { it.orderId }
            .mapValues { (_, claims) -> claims.maxBy { it.claimId } }

    fun decryptPii(encoded: String): String = piiStringCodec.decrypt(encoded)

    private fun condition(query: SellerOrderSearchQuery): SellerOrderListCondition =
        SellerOrderListCondition(
            sellerId = query.sellerId,
            statuses = query.filter.statuses,
            keyword = query.keyword,
            orderedFrom = query.orderedFrom?.atStartOfDay(),
            orderedToExclusive = query.orderedTo?.let(::nextDayStart),
            offset = query.page.toLong() * query.size,
            limit = query.size,
        )

    /** 상한 날짜를 통째로 포함하도록 [from, to+1일) 반개구간으로 환산 */
    private fun nextDayStart(date: LocalDate): LocalDateTime = date.plusDays(1).atStartOfDay()
}
