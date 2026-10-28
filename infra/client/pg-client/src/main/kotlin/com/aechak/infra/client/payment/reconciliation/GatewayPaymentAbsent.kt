package com.aechak.infra.client.payment.reconciliation

/** onStatus 핸들러는 값을 못 돌려주므로 "PG가 모르는 결제"를 이 예외로 올려 호출부에서 null로 바꾼다 */
internal class GatewayPaymentAbsent : RuntimeException()
