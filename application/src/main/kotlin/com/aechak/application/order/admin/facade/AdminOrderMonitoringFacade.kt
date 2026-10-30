package com.aechak.application.order.admin.facade

import com.aechak.application.order.admin.port.view.AdminOrderClaimView
import com.aechak.application.order.admin.port.view.AdminOrderLineView
import com.aechak.application.order.admin.port.view.AdminOrderShipmentView
import com.aechak.application.order.admin.port.view.AdminOrderSummaryView
import com.aechak.application.order.admin.service.AdminOrderDispatchNudgeService
import com.aechak.application.order.admin.service.AdminOrderMonitoringService
import com.aechak.application.order.admin.support.OrderDelayDeadlines
import com.aechak.application.order.admin.usecase.AdminOrderMonitoringUseCase
import com.aechak.application.order.admin.usecase.command.NudgeOrderDispatchCommand
import com.aechak.application.order.admin.usecase.query.AdminOrderMetricsQuery
import com.aechak.application.order.admin.usecase.query.AdminOrderSearchQuery
import com.aechak.application.order.admin.usecase.result.AdminOrderDetailResult
import com.aechak.application.order.admin.usecase.result.AdminOrderDispatchNudgeResult
import com.aechak.application.order.admin.usecase.result.AdminOrderLineResult
import com.aechak.application.order.admin.usecase.result.AdminOrderMetricsResult
import com.aechak.application.order.admin.usecase.result.AdminOrderStatusHistoryResult
import com.aechak.application.order.admin.usecase.result.AdminOrderSummaryResult
import com.aechak.application.support.CursorPageResult
import com.aechak.application.user.user.usecase.UserUseCase
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime

/**
 * 어드민 주문 배송 모니터링 조립.
 *
 * 자격 게이트가 없다 — 어드민 실행 모듈의 SecurityConfig가 헬스체크·문서를 뺀 전 요청을
 * role=ADMIN 전용으로 잠그므로, 이 계층이 다시 판정할 재료도 이유도 없다.
 */
@Service
class AdminOrderMonitoringFacade(
    private val adminOrderMonitoringService: AdminOrderMonitoringService,
    private val adminOrderDispatchNudgeService: AdminOrderDispatchNudgeService,
    private val userUseCase: UserUseCase,
) : AdminOrderMonitoringUseCase {
    @Transactional(readOnly = true)
    override fun getOrders(query: AdminOrderSearchQuery): CursorPageResult<AdminOrderSummaryResult> {
        val deadlines = adminOrderMonitoringService.deadlines(LocalDateTime.now())
        val page = adminOrderMonitoringService.getPage(query, deadlines)
        val bundle = bundleOf(page.items, deadlines)
        return page.map { bundle.assemble(it) }
    }

    @Transactional(readOnly = true)
    override fun getOrderMetrics(query: AdminOrderMetricsQuery): AdminOrderMetricsResult =
        adminOrderMonitoringService.countMetrics(query, adminOrderMonitoringService.deadlines(LocalDateTime.now()))

    @Transactional(readOnly = true)
    override fun getOrderDetail(orderPublicId: String): AdminOrderDetailResult {
        val view = adminOrderMonitoringService.getOne(orderPublicId)
        val deadlines = adminOrderMonitoringService.deadlines(LocalDateTime.now())
        val lines = adminOrderMonitoringService.getLines(listOf(view.orderId))
        return AdminOrderDetailResult(
            order = bundleOf(listOf(view), deadlines, lines).assemble(view),
            orderGroupId = view.orderGroupPublicId,
            purchaseConfirmedAt = view.purchaseConfirmedAt,
            confirmType = view.confirmType,
            items = lines.map(AdminOrderLineResult::from),
            statusHistory =
                adminOrderMonitoringService
                    .getStatusHistories(view.orderId)
                    .map(AdminOrderStatusHistoryResult::from),
            nudges =
                adminOrderDispatchNudgeService
                    .getHistory(view.orderId)
                    .map(AdminOrderDispatchNudgeResult::from),
        )
    }

    @Transactional
    override fun nudgeDispatch(command: NudgeOrderDispatchCommand): AdminOrderDispatchNudgeResult =
        AdminOrderDispatchNudgeResult.from(adminOrderDispatchNudgeService.nudge(command, LocalDateTime.now()))

    /** 품목·배송·클레임·구매자 닉네임을 페이지 단위로 한 번에 받는다 — 주문 수만큼 쿼리가 늘지 않게. */
    private fun bundleOf(
        orders: List<AdminOrderSummaryView>,
        deadlines: OrderDelayDeadlines,
        lines: List<AdminOrderLineView>? = null,
    ): OrderBundle {
        val orderIds = orders.map { it.orderId }
        return OrderBundle(
            lines = (lines ?: adminOrderMonitoringService.getLines(orderIds)).groupBy { it.orderId },
            shipments = adminOrderMonitoringService.getLatestShipments(orderIds),
            claims = adminOrderMonitoringService.getOpenClaims(orderIds),
            nicknames = userUseCase.getAuthors(orders.map { it.buyerId }).mapValues { it.value.nickname },
            deadlines = deadlines,
        )
    }

    private class OrderBundle(
        private val lines: Map<Long, List<AdminOrderLineView>>,
        private val shipments: Map<Long, AdminOrderShipmentView>,
        private val claims: Map<Long, AdminOrderClaimView>,
        private val nicknames: Map<Long, String>,
        private val deadlines: OrderDelayDeadlines,
    ) {
        fun assemble(view: AdminOrderSummaryView): AdminOrderSummaryResult =
            AdminOrderSummaryResult.of(
                view = view,
                buyerNickname = nicknames.getValue(view.buyerId),
                lines = lines[view.orderId].orEmpty(),
                shipment = shipments[view.orderId],
                claim = claims[view.orderId],
                delayReason = deadlines.reasonFor(view.status, view.orderedAt),
            )
    }
}
