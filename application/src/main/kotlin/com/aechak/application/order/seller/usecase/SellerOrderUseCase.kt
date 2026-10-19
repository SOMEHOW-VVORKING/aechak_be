package com.aechak.application.order.seller.usecase

import com.aechak.application.order.seller.usecase.command.AcceptOrderCommand
import com.aechak.application.order.seller.usecase.query.SellerOrderSearchQuery
import com.aechak.application.order.seller.usecase.result.SellerOrderDetailResult
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
}
