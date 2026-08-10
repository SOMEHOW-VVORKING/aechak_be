package com.aechak.api.user.verification.response

import com.aechak.application.user.verification.usecase.result.PhoneCodeSentResult

/** FE는 이 값으로 만료·재전송 타이머를 그린다 — 수치 하드코딩 금지 계약. */
data class PhoneCodeSentResponse(
    val expiresInSeconds: Int,
    val resendCooldownSeconds: Int,
) {
    companion object {
        fun from(result: PhoneCodeSentResult): PhoneCodeSentResponse =
            PhoneCodeSentResponse(
                expiresInSeconds = result.expiresInSeconds,
                resendCooldownSeconds = result.resendCooldownSeconds,
            )
    }
}
