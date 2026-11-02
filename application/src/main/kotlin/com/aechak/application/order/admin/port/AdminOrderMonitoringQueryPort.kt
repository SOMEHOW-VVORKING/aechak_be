package com.aechak.application.order.admin.port

import com.aechak.application.order.admin.port.view.AdminOrderClaimView
import com.aechak.application.order.admin.port.view.AdminOrderLineView
import com.aechak.application.order.admin.port.view.AdminOrderShipmentView
import com.aechak.application.order.admin.port.view.AdminOrderStatusHistoryView
import com.aechak.application.order.admin.port.view.AdminOrderSummaryView

/**
 * 운영자용 전체 주문 목록·상세 조회 포트.
 * 품목·배송·클레임은 주문 묶음 단위로 받아 호출자가 메모리에서 묶는다 — 주문 수만큼 쿼리가 늘지 않게.
 */
interface AdminOrderMonitoringQueryPort {
    fun findPage(condition: AdminOrderMonitorCondition): List<AdminOrderSummaryView>

    /** 결제대기 주문은 없는 것으로 본다 — 목록에 없는 건을 상세로 열 수 있으면 안 된다 */
    fun findOne(orderPublicId: String): AdminOrderSummaryView?

    fun findLines(orderIds: Collection<Long>): List<AdminOrderLineView>

    /**
     * **출고 배송만 돌려준다** — 반품·교환 회수 송장은 구매자에서 셀러로 가는 역방향이라 이 화면이
     * 말하는 "배송"이 아니고, 배송 지연 시계도 되돌리면 안 된다. 뷰에 종류가 없으니 호출자는
     * 걸러진 것을 전제한다. 주문당 여러 행일 수 있어 표시할 한 건은 호출자가 고른다.
     */
    fun findShipments(orderIds: Collection<Long>): List<AdminOrderShipmentView>

    /** 종결 시각이 없고 더 진행될 수 있는 클레임만 — 주문당 여러 행일 수 있어 호출자가 최신 한 건을 고른다 */
    fun findOpenClaims(orderIds: Collection<Long>): List<AdminOrderClaimView>

    /** 전이 시각 오름차순 — 타임라인 표시 순서 */
    fun findStatusHistories(orderId: Long): List<AdminOrderStatusHistoryView>
}
