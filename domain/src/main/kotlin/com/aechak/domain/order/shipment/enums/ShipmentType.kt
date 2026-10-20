package com.aechak.domain.order.shipment.enums

enum class ShipmentType {
    INITIAL,

    /** 반품·교환 회수 송장 — 구매자에서 셀러로 가는 역방향 배송. */
    RETURN_COLLECT,

    EXCHANGE_RESHIP,
}
