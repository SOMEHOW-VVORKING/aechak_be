package com.aechak.application.order.seller.service

import com.aechak.application.order.seller.port.SellerOrderListCondition
import com.aechak.application.order.seller.port.SellerOrderQueryPort
import com.aechak.application.order.seller.port.view.SellerOrderClaimView
import com.aechak.application.order.seller.port.view.SellerOrderLineView
import com.aechak.application.order.seller.port.view.SellerOrderShipmentView
import com.aechak.application.order.seller.port.view.SellerOrderSummaryView
import com.aechak.application.order.seller.usecase.command.AcceptOrderCommand
import com.aechak.application.order.seller.usecase.command.RegisterShipmentCommand
import com.aechak.application.order.seller.usecase.query.SellerOrderSearchQuery
import com.aechak.application.order.seller.usecase.result.RegisterShipmentResult
import com.aechak.application.pii.support.PiiStringCodec
import com.aechak.application.support.OffsetPageResult
import com.aechak.common.error.BusinessException
import com.aechak.domain.order.error.OrderErrorCode
import com.aechak.domain.order.order.Order
import com.aechak.domain.order.order.OrderStatusHistory
import com.aechak.domain.order.order.enums.OrderStatus
import com.aechak.domain.order.order.enums.TriggeredBy
import com.aechak.domain.order.order.repository.OrderRepository
import com.aechak.domain.order.order.repository.OrderStatusHistoryRepository
import com.aechak.domain.order.shipment.enums.ShipmentType
import com.aechak.domain.order.shipment.repository.CourierMasterRepository
import com.aechak.domain.order.shipment.repository.ShipmentRepository
import org.springframework.stereotype.Service
import java.time.LocalDate
import java.time.LocalDateTime

@Service
class SellerOrderService(
    private val orderRepository: OrderRepository,
    private val orderStatusHistoryRepository: OrderStatusHistoryRepository,
    private val sellerOrderQueryPort: SellerOrderQueryPort,
    private val piiStringCodec: PiiStringCodec,
    private val courierMasterRepository: CourierMasterRepository,
    private val shipmentRepository: ShipmentRepository,
) {
    fun acceptOrder(command: AcceptOrderCommand) {
        val order = findSellerOrder(command.sellerId, command.orderPublicId)

        // 전이의 심판은 조건부 UPDATE다. 경합에서 진 쪽은 0행을 받는다
        if (!orderRepository.transitionStatusIf(order.id, OrderStatus.PAID, OrderStatus.PREPARING)) {
            throw BusinessException(OrderErrorCode.ORDER_NOT_ACCEPTABLE)
        }
        order.accept()

        recordHistory(order, OrderStatus.PAID, OrderStatus.PREPARING)
    }

    fun registerShipment(command: RegisterShipmentCommand): RegisterShipmentResult {
        val order = findSellerOrder(command.sellerId, command.orderPublicId)

        if (shipmentRepository.findByOrderIdAndShipmentType(order.id, ShipmentType.INITIAL) != null) {
            throw BusinessException(OrderErrorCode.SHIPMENT_ALREADY_REGISTERED)
        }
        if (order.status !in DISPATCHABLE_STATUSES) {
            throw BusinessException(OrderErrorCode.ORDER_NOT_DISPATCHABLE)
        }

        val courier =
            courierMasterRepository.findActiveByCourierCode(command.courierCode)
                ?: throw BusinessException(OrderErrorCode.COURIER_NOT_FOUND)
        courier.validateTrackingNumber(command.trackingNumber)

        // 애그리거트가 dirty해지기 전에 두 전이를 모두 선점한다 — 더티체킹 플러시가 조건부 UPDATE를 앞지르면 0행이 된다
        val fromPreparing = order.status == OrderStatus.PREPARING
        if (fromPreparing) {
            transitionOrThrow(order.id, OrderStatus.PREPARING, OrderStatus.DISPATCH_PENDING)
        }
        transitionOrThrow(order.id, OrderStatus.DISPATCH_PENDING, OrderStatus.SHIPPING)

        if (fromPreparing) {
            order.markDispatchPending()
            recordHistory(order, OrderStatus.PREPARING, OrderStatus.DISPATCH_PENDING)
        }
        order.markShipping()
        recordHistory(order, OrderStatus.DISPATCH_PENDING, OrderStatus.SHIPPING)

        val dispatchedAt = LocalDateTime.now()
        val shipment = shipmentRepository.save(command.toEntity(courier, order, dispatchedAt))

        return RegisterShipmentResult(
            shipmentId = shipment.id,
            dispatchedAt = dispatchedAt,
            orderStatus = order.status,
        )
    }

    private fun findSellerOrder(
        sellerId: Long,
        orderPublicId: String,
    ): Order =
        orderRepository.findByPublicIdAndSellerId(orderPublicId, sellerId)
            ?: throw BusinessException(OrderErrorCode.ORDER_NOT_FOUND)

    private fun transitionOrThrow(
        orderId: Long,
        expected: OrderStatus,
        next: OrderStatus,
    ) {
        if (!orderRepository.transitionStatusIf(orderId, expected, next)) {
            throw BusinessException(OrderErrorCode.ORDER_NOT_DISPATCHABLE)
        }
    }

    private fun recordHistory(
        order: Order,
        fromStatus: OrderStatus,
        toStatus: OrderStatus,
    ) {
        orderStatusHistoryRepository.save(
            OrderStatusHistory.record(
                order = order,
                fromStatus = fromStatus,
                toStatus = toStatus,
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

    private companion object {
        /** 상품 준비중에서 등록하면 발송 대기를 거쳐 2단 전이한다 — enum이 PREPARING → SHIPPING 직행을 허용하지 않는다. */
        private val DISPATCHABLE_STATUSES = setOf(OrderStatus.PREPARING, OrderStatus.DISPATCH_PENDING)
    }
}
