package com.aechak.application.order.admin.usecase

import com.aechak.application.order.admin.usecase.command.NudgeOrderDispatchCommand
import com.aechak.application.order.admin.usecase.query.AdminOrderMetricsQuery
import com.aechak.application.order.admin.usecase.query.AdminOrderSearchQuery
import com.aechak.application.order.admin.usecase.result.AdminOrderDetailResult
import com.aechak.application.order.admin.usecase.result.AdminOrderDispatchNudgeResult
import com.aechak.application.order.admin.usecase.result.AdminOrderMetricsResult
import com.aechak.application.order.admin.usecase.result.AdminOrderSummaryResult
import com.aechak.application.support.CursorPageResult

/**
 * 운영자용 전체 주문 배송 모니터링 진입점.
 *
 * 자격(role=ADMIN)은 어드민 실행 모듈의 SecurityConfig가 전역 보증하므로 이 계층에 자격 게이트가 없다.
 * 소유권 개념도 없다 — 운영자는 특정 셀러·구매자의 주문이 아니라 전체를 본다.
 */
interface AdminOrderMonitoringUseCase {
    fun getOrders(query: AdminOrderSearchQuery): CursorPageResult<AdminOrderSummaryResult>

    fun getOrderMetrics(query: AdminOrderMetricsQuery): AdminOrderMetricsResult

    fun getOrderDetail(orderPublicId: String): AdminOrderDetailResult

    /**
     * 셀러 발송 독촉 기록. **알림을 보내지 않는다** — 어드민 실행 모듈이 발송 어댑터를 조립하지 않아
     * 보낼 수단이 없다. 기록만 남기고, 발송이 붙으면 이 행을 트리거의 근거로 쓴다.
     */
    fun nudgeDispatch(command: NudgeOrderDispatchCommand): AdminOrderDispatchNudgeResult
}
