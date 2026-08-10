package com.aechak.api.user.verification.request

import com.aechak.application.user.verification.usecase.command.SendPhoneCodeCommand
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Pattern

object PhoneVerificationPatterns {
    /** 구분자는 하이픈만 허용 — 서버가 숫자만 남겨 정규화한다(계약: 형식 위반은 90001). */
    const val PHONE_NUMBER = "^01[016789]-?\\d{3,4}-?\\d{4}$"
    const val CODE = "^\\d{6}$"
}

data class SendPhoneCodeRequest(
    @field:NotBlank(message = "휴대폰 번호를 입력해 주세요.")
    @field:Pattern(regexp = PhoneVerificationPatterns.PHONE_NUMBER, message = "휴대폰 번호 형식이 올바르지 않습니다.")
    val phoneNumber: String,
) {
    fun toCommand(userId: Long): SendPhoneCodeCommand = SendPhoneCodeCommand(userId = userId, phoneNumber = phoneNumber)
}
