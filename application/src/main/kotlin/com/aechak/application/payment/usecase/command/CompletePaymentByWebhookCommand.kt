package com.aechak.application.payment.usecase.command

/** 웹훅 본문·서명 헤더 원문. 검증 전이라 아직 우리 어휘가 아니다 */
data class CompletePaymentByWebhookCommand(
    val rawBody: String,
    val webhookId: String?,
    val webhookTimestamp: String?,
    val webhookSignature: String?,
)
