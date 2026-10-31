package com.aechak.domain.order.shipment.enums

enum class ShipmentType {
    INITIAL,

    /** 반품·교환 회수 송장 — 구매자에서 셀러로 가는 역방향 배송. */
    RETURN_COLLECT,

    EXCHANGE_RESHIP,
    ;

    companion object {
        /**
         * 구매자에게 가는 방향의 배송. 회수 송장은 역방향이라 "주문의 배송"이 아니다 —
         * 운영 화면이 표시하는 배송과 배송 지연 시계가 이 집합만 본다.
         */
        val OUTBOUND: Set<ShipmentType> = setOf(INITIAL, EXCHANGE_RESHIP)
    }
}
