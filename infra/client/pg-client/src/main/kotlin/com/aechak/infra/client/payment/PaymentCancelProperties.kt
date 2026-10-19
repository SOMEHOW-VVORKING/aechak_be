package com.aechak.infra.client.payment

import org.springframework.boot.context.properties.ConfigurationProperties

/**
 * 취소 요청 1건의 금액 상한. 어댑터가 PG로 나가기 직전에 대조해 초과하면 거절한다.
 * 기본 0 = 취소를 내보내지 않음 — 상점이 테스트 상점인지 확인되지 않은 환경에서
 * 설정 누락이 조용히 실호출로 이어지지 않게 닫힌 상태로 둔다.
 */
@ConfigurationProperties("payment.cancel")
data class PaymentCancelProperties(
    val maxAmount: Long = 0L,
)
