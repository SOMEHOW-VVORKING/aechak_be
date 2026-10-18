package com.aechak.application.order.seller.port

import com.aechak.application.order.seller.port.view.SellerOrderClaimView
import com.aechak.application.order.seller.port.view.SellerOrderLineView
import com.aechak.application.order.seller.port.view.SellerOrderShipmentView
import com.aechak.application.order.seller.port.view.SellerOrderSummaryView

/**
 * 셀러 자신의 주문 목록·상세 조회 포트.
 * 품목·배송·클레임은 주문 묶음 단위로 받아 호출자가 메모리에서 묶는다 — 주문 수만큼 쿼리가 늘지 않게.
 */
interface SellerOrderQueryPort {
    fun findPage(condition: SellerOrderListCondition): List<SellerOrderSummaryView>

    fun count(condition: SellerOrderListCondition): Long

    /** 셀러 스코프 단건 조회 — 타 셀러 주문과 결제대기 주문은 없는 것으로 본다 */
    fun findOwned(
        orderPublicId: String,
        sellerId: Long,
    ): SellerOrderSummaryView?

    fun findLines(orderIds: Collection<Long>): List<SellerOrderLineView>

    fun findShipments(orderIds: Collection<Long>): List<SellerOrderShipmentView>

    /** 종결되지 않은 클레임만 — 주문당 여러 행일 수 있어 호출자가 최신 한 건을 고른다 */
    fun findOpenClaims(orderIds: Collection<Long>): List<SellerOrderClaimView>
}
