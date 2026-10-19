package com.aechak.application.order.seller.usecase

import com.aechak.application.order.seller.usecase.command.AcceptOrderCommand

/**
 * 셀러센터가 쓰는 주문 진입점 계약. 구매자 EP가 쓰는 OrderUseCase와 나눠 둔다 —
 * 소비하는 실행 모듈이 갈리고 메서드가 겹치지 않는다.
 */
interface SellerOrderUseCase {
    /** 발주 확인 — 결제완료 주문을 상품 준비중으로 전이하고 전이 이력을 남긴다. */
    fun acceptOrder(command: AcceptOrderCommand)

    /** 택배사 코드가 활성 마스터에 있는지 확인한다 — 없거나 비활성이면 예외. */
    fun verifyActiveCourier(courierCode: String)
}
