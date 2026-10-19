package com.aechak.api.payment

import com.aechak.application.payment.usecase.PaymentUseCase
import com.aechak.application.payment.usecase.command.CompletePaymentByWebhookCommand
import com.aechak.common.error.BusinessException
import com.aechak.domain.payment.error.PaymentErrorCode
import org.slf4j.LoggerFactory
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/**
 * 포트원이 보내는 결제 통보 수신구. 로그인 없이 열려 있고 신원은 서명이 보증한다.
 * 확정 자체는 콜백과 같은 유스케이스를 탄다 — 통보 내용은 참고일 뿐 진실은 서버가 포트원에 다시 묻는다.
 */
@RestController
@RequestMapping("/webhooks/portone")
class PaymentWebhookController(
    private val paymentUseCase: PaymentUseCase,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    /** 본문은 파싱하지 않고 문자열로 받는다 — 서명이 받은 원문 기준이라 재직렬화하면 깨진다 */
    @PostMapping
    fun receive(
        @RequestBody rawBody: String,
        @RequestHeader(WEBHOOK_ID, required = false) webhookId: String?,
        @RequestHeader(WEBHOOK_TIMESTAMP, required = false) webhookTimestamp: String?,
        @RequestHeader(WEBHOOK_SIGNATURE, required = false) webhookSignature: String?,
    ): ResponseEntity<Void> =
        try {
            paymentUseCase.completePaymentByWebhook(
                CompletePaymentByWebhookCommand(
                    rawBody = rawBody,
                    webhookId = webhookId,
                    webhookTimestamp = webhookTimestamp,
                    webhookSignature = webhookSignature,
                ),
            )
            ResponseEntity.ok().build()
        } catch (e: BusinessException) {
            // 실패로 돌려주면 포트원이 재전송한다. 재전송이 결과를 바꿀 수 있는 것(일시 장애)과
            // 발신자를 막아야 하는 것(서명)만 그대로 올리고, 나머지는 몇 번을 다시 받아도 결론이 같아 200으로 접는다.
            if (e.errorCode in RESEND_WORTHY) throw e
            log.warn("웹훅 확정을 접음 — 재전송해도 결과가 같음. errorCode={}", e.errorCode.code, e)
            ResponseEntity.ok().build()
        }

    companion object {
        // Standard Webhooks 규격 헤더 이름
        private const val WEBHOOK_ID = "webhook-id"
        private const val WEBHOOK_TIMESTAMP = "webhook-timestamp"
        private const val WEBHOOK_SIGNATURE = "webhook-signature"

        private val RESEND_WORTHY =
            setOf(
                PaymentErrorCode.PAYMENT_GATEWAY_ERROR, // 포트원 조회 실패·설정 미비 — 고치면 다음 전송이 성공한다
                PaymentErrorCode.PAYMENT_WEBHOOK_INVALID_SIGNATURE, // 우리 발신자가 아니다 — 401로 거절
            )
    }
}
