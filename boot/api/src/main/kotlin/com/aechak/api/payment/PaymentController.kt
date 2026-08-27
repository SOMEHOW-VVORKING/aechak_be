package com.aechak.api.payment

import com.aechak.api.payment.config.PaymentStoreProperties
import com.aechak.api.payment.request.PreparePaymentRequest
import com.aechak.api.payment.response.CompletePaymentResponse
import com.aechak.api.payment.response.PreparePaymentResponse
import com.aechak.application.payment.usecase.PaymentUseCase
import com.aechak.application.payment.usecase.command.CompletePaymentCommand
import com.aechak.common.error.BusinessException
import com.aechak.domain.payment.error.PaymentErrorCode
import com.aechak.webcommon.response.ApiResponse
import com.aechak.websecurity.authentication.AuthPrincipal
import jakarta.validation.Valid
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/order-groups")
class PaymentController(
    private val paymentUseCase: PaymentUseCase,
    private val store: PaymentStoreProperties,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @PostMapping("/{orderGroupId}/payment/prepare")
    fun preparePayment(
        @PathVariable orderGroupId: String,
        @Valid @RequestBody request: PreparePaymentRequest,
        @AuthenticationPrincipal principal: AuthPrincipal,
    ): ResponseEntity<ApiResponse<PreparePaymentResponse>> {
        validateStoreConfig()
        val result = paymentUseCase.preparePayment(request.toCommand(principal.userId, orderGroupId))
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.of(PreparePaymentResponse.of(result, store)))
    }

    /** 결제창이 닫힌 뒤 FE가 부르는 확정 콜백 — 본문 없음, 진실은 서버가 포트원에 직접 확인한다 */
    @PostMapping("/{orderGroupId}/payment/complete")
    fun completePayment(
        @PathVariable orderGroupId: String,
        @AuthenticationPrincipal principal: AuthPrincipal,
    ): ResponseEntity<ApiResponse<CompletePaymentResponse>> {
        val result = paymentUseCase.completePayment(CompletePaymentCommand(principal.userId, orderGroupId))
        return ResponseEntity.ok(ApiResponse.of(CompletePaymentResponse.from(result)))
    }

    private fun validateStoreConfig() {
        if (store.id.isBlank() || store.channelKey.isBlank()) {
            log.error("포트원 상점 ID나 채널 키가 설정되지 않아 결제 준비를 중단함")
            throw BusinessException(PaymentErrorCode.PAYMENT_GATEWAY_ERROR)
        }
    }
}
