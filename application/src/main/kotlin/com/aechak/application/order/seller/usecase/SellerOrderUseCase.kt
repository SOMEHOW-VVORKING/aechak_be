package com.aechak.application.order.seller.usecase

import com.aechak.application.order.seller.usecase.command.AcceptOrderCommand
import com.aechak.application.order.seller.usecase.command.RegisterShipmentCommand
import com.aechak.application.order.seller.usecase.query.SellerOrderMetricsQuery
import com.aechak.application.order.seller.usecase.query.SellerOrderSearchQuery
import com.aechak.application.order.seller.usecase.result.RegisterShipmentResult
import com.aechak.application.order.seller.usecase.result.SellerOrderDetailResult
import com.aechak.application.order.seller.usecase.result.SellerOrderMetricsResult
import com.aechak.application.order.seller.usecase.result.SellerOrderSummaryResult
import com.aechak.application.support.OffsetPageResult

/**
 * 셀러센터가 쓰는 주문 진입점 계약. 구매자 EP가 쓰는 OrderUseCase와 나눠 둔다 —
 * 소비하는 실행 모듈이 갈리고 메서드가 겹치지 않는다.
 */
interface SellerOrderUseCase {
    /** 발주 확인 — 결제완료 주문을 상품 준비중으로 전이하고 전이 이력을 남긴다. */
    fun acceptOrder(command: AcceptOrderCommand)

    /** 내 주문 목록 — 상태 세그먼트·키워드·주문일 범위 필터와 오프셋 페이지네이션. */
    fun getMyOrders(query: SellerOrderSearchQuery): OffsetPageResult<SellerOrderSummaryResult>

    /** 내 주문 상세 — 목록 행과 같은 주문 단위 값에 품목을 더한다. */
    fun getMyOrderDetail(
        sellerId: Long,
        orderPublicId: String,
    ): SellerOrderDetailResult

    /** 송장 등록 — 최초 발송 1건을 만들고 주문을 배송중으로 전이한다. */
    fun registerShipment(command: RegisterShipmentCommand): RegisterShipmentResult

    /**
     * 대시보드용 주문 지표 집계 — 신규 주문·발송 대기·발송 지연을 쿼리 한 번으로 센다.
     *
     * 전제 — 셀러 자격 게이트는 호출자가 통과시킨다. 집계는 넘겨받은 sellerId 범위만 읽고
     * 식별자를 돌려주지 않으므로 소유권 판정 대상이 아니다. 이 계약을 HTTP로 직접 노출하지 않는다.
     */
    fun getOrderMetrics(query: SellerOrderMetricsQuery): SellerOrderMetricsResult
}
