package com.aechak.application.user.verification.usecase.result

/** 발송 결과 — FE가 만료·재전송 타이머를 그리는 원천 값. */
data class PhoneCodeSentResult(
    val expiresInSeconds: Int,
    val resendCooldownSeconds: Int,
)
