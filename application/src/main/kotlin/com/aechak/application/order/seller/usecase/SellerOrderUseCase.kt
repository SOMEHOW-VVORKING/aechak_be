package com.aechak.application.order.seller.usecase

import com.aechak.application.order.seller.usecase.command.AcceptOrderCommand
import com.aechak.application.order.seller.usecase.command.CompleteDeliveryCommand
import com.aechak.application.order.seller.usecase.command.RegisterShipmentCommand
import com.aechak.application.order.seller.usecase.result.RegisterShipmentResult

/**
 * 셀러센터가 쓰는 주문 진입점 계약. 구매자 EP가 쓰는 OrderUseCase와 나눠 둔다 —
 * 소비하는 실행 모듈이 갈리고 메서드가 겹치지 않는다.
 */
interface SellerOrderUseCase {
    /** 발주 확인 — 결제완료 주문을 상품 준비중으로 전이하고 전이 이력을 남긴다. */
    fun acceptOrder(command: AcceptOrderCommand)

    /** 송장 등록 — 최초 발송 1건을 만들고 주문을 배송중으로 전이한다. */
    fun registerShipment(command: RegisterShipmentCommand): RegisterShipmentResult

    /** 배송완료 — 배송중 주문을 배송완료로 전이하고 구매확정 기한을 부여한다. */
    fun completeDelivery(command: CompleteDeliveryCommand)
}
