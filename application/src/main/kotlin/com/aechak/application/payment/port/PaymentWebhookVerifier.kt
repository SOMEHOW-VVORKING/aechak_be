package com.aechak.application.payment.port

/**
 * 결제 게이트웨이가 보낸 웹훅의 진위를 가리고 우리 어휘로 옮긴다.
 * 서명 규격과 본문 스키마는 게이트웨이마다 달라 어댑터 안에만 둔다.
 */
interface PaymentWebhookVerifier {
    /**
     * 서명이 유효하지 않으면 예외를 던진다.
     * 유효하지만 결제 확정과 무관한 사건(가상계좌 발급 등)이면 null.
     */
    fun verify(request: PaymentWebhookRequest): PaymentWebhookNotification?
}

/** 검증 대상 원문. 본문은 파싱 전 문자열이어야 한다 — 다시 직렬화하면 서명이 깨진다 */
data class PaymentWebhookRequest(
    val rawBody: String,
    val id: String?,
    val timestamp: String?,
    val signature: String?,
)

data class PaymentWebhookNotification(
    val paymentId: String,
    val type: PaymentWebhookNotificationType,
)

/** 확정 흐름을 태울 사건만 추린 것. 결과 판정은 게이트웨이 조회가 하므로 종류는 로그용이다 */
enum class PaymentWebhookNotificationType {
    PAID,
    FAILED,
}
