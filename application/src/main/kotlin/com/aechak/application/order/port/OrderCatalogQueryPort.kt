package com.aechak.application.order.port

import com.aechak.application.order.port.view.OrderCatalogItemView

/** 상품과 셀러는 옵션 조합에서 파생함. */
interface OrderCatalogQueryPort {
    /**
     * 주문 생성 검증·계산용 복수 조회. 존재하는 것만 돌려주고 상태로 걸러내지 않는다 —
     * 주문 불가 판정은 호출측이 명시적 실패로 처리한다.
     */
    fun findItems(optionCombinationIds: Collection<Long>): List<OrderCatalogItemView>
}
