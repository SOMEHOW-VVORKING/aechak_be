package com.aechak.application.order.admin.service

import com.aechak.application.order.admin.port.AdminOrderCursor
import com.aechak.application.order.admin.port.AdminOrderMetricsCondition
import com.aechak.application.order.admin.port.AdminOrderMetricsQueryPort
import com.aechak.application.order.admin.port.AdminOrderMonitorCondition
import com.aechak.application.order.admin.port.AdminOrderMonitoringQueryPort
import com.aechak.application.order.admin.port.view.AdminOrderClaimView
import com.aechak.application.order.admin.port.view.AdminOrderLineView
import com.aechak.application.order.admin.port.view.AdminOrderShipmentView
import com.aechak.application.order.admin.port.view.AdminOrderStatusHistoryView
import com.aechak.application.order.admin.port.view.AdminOrderSummaryView
import com.aechak.application.order.admin.support.AdminOrderCursorCodec
import com.aechak.application.order.admin.support.OrderDelayDeadlines
import com.aechak.application.order.admin.usecase.query.AdminOrderMetricsQuery
import com.aechak.application.order.admin.usecase.query.AdminOrderSearchQuery
import com.aechak.application.order.admin.usecase.result.AdminOrderMetricsResult
import com.aechak.application.support.CursorPageResult
import com.aechak.application.support.CursorPageSize
import com.aechak.common.error.BusinessException
import com.aechak.common.error.CommonErrorCode
import com.aechak.domain.order.error.OrderErrorCode
import com.aechak.domain.order.order.enums.OrderStatus
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import java.time.LocalDate
import java.time.LocalDateTime

/**
 * 어드민 주문 모니터링 조회.
 *
 * **지연 기준은 서버가 쥔다.** 발송 기한은 셀러 대시보드가 이미 쓰는 `order.dispatch.sla-days`를
 * 그대로 읽는다 — 셀러가 신고하는 `avgDispatchDays`(1~7 영업일)를 쓰면 ① 같은 주문을 셀러 화면과
 * 어드민 화면이 다른 기준으로 판정하고 ② 셀러마다 기준이 달라 "지연 N건"이 셀러 간 비교 불가가 되며
 * ③ 영업일을 날짜로 환산할 휴일 달력이 없어 값 자체를 정확히 쓸 수 없다.
 * 배송 정체 기한(`order.delivery.sla-days`)은 셀러 화면에 없는 어드민 전용 축이다.
 */
@Service
class AdminOrderMonitoringService(
    private val queryPort: AdminOrderMonitoringQueryPort,
    private val metricsQueryPort: AdminOrderMetricsQueryPort,
    @param:Value("\${order.dispatch.sla-days:3}") private val dispatchSlaDays: Long,
    @param:Value("\${order.delivery.sla-days:7}") private val deliverySlaDays: Long,
) {
    /** 기준 시각은 호출자가 한 번 떠서 넘긴 것만 쓴다 — 여기서 시계를 뜨면 한 응답의 목록과 지표가 갈린다 */
    fun deadlines(at: LocalDateTime): OrderDelayDeadlines =
        OrderDelayDeadlines(
            dispatchDeadline = at.minusDays(dispatchSlaDays),
            deliveryDeadline = at.minusDays(deliverySlaDays),
        )

    fun getPage(
        query: AdminOrderSearchQuery,
        deadlines: OrderDelayDeadlines,
    ): CursorPageResult<AdminOrderSummaryView> {
        val condition =
            AdminOrderMonitorCondition(
                statuses = statusesOf(query.status),
                sellerId = query.sellerId,
                keyword = query.keyword?.trim()?.takeIf { it.isNotEmpty() },
                orderedFrom = query.orderedFrom?.atStartOfDay(),
                orderedToExclusive = query.orderedTo?.let(::nextDayStart),
                delayedOnly = query.delayedOnly,
                deadlines = deadlines,
                cursor = query.cursor?.let(AdminOrderCursorCodec::decode),
                limit = CursorPageSize.fetchLimit(query.size),
            )
        return CursorPageResult.of(
            fetched = queryPort.findPage(condition),
            size = query.size,
        ) { AdminOrderCursorCodec.encode(AdminOrderCursor(it.orderedAt, it.orderId)) }
    }

    fun getOne(orderPublicId: String): AdminOrderSummaryView =
        queryPort.findOne(orderPublicId) ?: throw BusinessException(OrderErrorCode.ORDER_NOT_FOUND)

    fun getLines(orderIds: Collection<Long>): List<AdminOrderLineView> = queryPort.findLines(orderIds)

    /** 주문당 최신 배송 한 건 — 교환 재발송이 붙으면 나중 행이 현재 배송이다 */
    fun getLatestShipments(orderIds: Collection<Long>): Map<Long, AdminOrderShipmentView> =
        queryPort
            .findShipments(orderIds)
            .groupBy { it.orderId }
            .mapValues { (_, shipments) -> shipments.maxBy { it.shipmentId } }

    /** 주문당 최신 진행 클레임 한 건 */
    fun getOpenClaims(orderIds: Collection<Long>): Map<Long, AdminOrderClaimView> =
        queryPort
            .findOpenClaims(orderIds)
            .groupBy { it.orderId }
            .mapValues { (_, claims) -> claims.maxBy { it.claimId } }

    fun getStatusHistories(orderId: Long): List<AdminOrderStatusHistoryView> = queryPort.findStatusHistories(orderId)

    fun countMetrics(
        query: AdminOrderMetricsQuery,
        deadlines: OrderDelayDeadlines,
    ): AdminOrderMetricsResult {
        val condition =
            AdminOrderMetricsCondition(
                countedStatuses = MONITORED_STATUSES,
                sellerId = query.sellerId,
                orderedFrom = query.orderedFrom?.atStartOfDay(),
                orderedToExclusive = query.orderedTo?.let(::nextDayStart),
                deadlines = deadlines,
            )
        return AdminOrderMetricsResult.of(
            counts = metricsQueryPort.countMetrics(condition),
            productAmount = metricsQueryPort.sumProductAmount(condition),
        )
    }

    /** 결제대기는 결제 전에도 테이블에 남는 행이라 모니터링 대상이 아니다 — 지정해 오면 거절한다 */
    private fun statusesOf(status: OrderStatus?): Set<OrderStatus> {
        if (status == null) return MONITORED_STATUSES
        if (status == OrderStatus.PENDING_PAYMENT) {
            throw BusinessException(CommonErrorCode.INVALID_REQUEST, detail = "status: 결제대기 주문은 조회 대상이 아닙니다.")
        }
        return setOf(status)
    }

    /** 상한 날짜를 통째로 포함하도록 [from, to+1일) 반개구간으로 환산 */
    private fun nextDayStart(date: LocalDate): LocalDateTime = date.plusDays(1).atStartOfDay()

    private companion object {
        private val MONITORED_STATUSES = OrderStatus.entries.toSet() - OrderStatus.PENDING_PAYMENT
    }
}
