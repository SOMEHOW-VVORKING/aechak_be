package com.aechak.application.order.delivery.usecase.command

/**
 * 추적 갱신 대상 한 건. 택배사 코드·송장번호를 함께 싣는다 —
 * 스캔이 이미 읽은 값이라 외부 조회에 필요한 택배사·송장 정보를 다시 읽지 않는다.
 */
data class SyncShipmentTrackingCommand(
    val shipmentId: Long,
    val courierCode: String,
    val trackingNumber: String,
)
