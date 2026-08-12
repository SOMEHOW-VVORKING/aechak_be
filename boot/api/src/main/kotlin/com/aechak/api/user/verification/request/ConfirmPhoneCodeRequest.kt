package com.aechak.api.user.verification.request

import com.aechak.application.user.verification.usecase.command.ConfirmPhoneCodeCommand
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Pattern

data class ConfirmPhoneCodeRequest(
    @field:NotBlank(message = "휴대폰 번호를 입력해 주세요.")
    @field:Pattern(regexp = PhoneVerificationPatterns.PHONE_NUMBER, message = "휴대폰 번호 형식이 올바르지 않습니다.")
    val phoneNumber: String,
    @field:NotBlank(message = "인증번호를 입력해 주세요.")
    @field:Pattern(regexp = PhoneVerificationPatterns.CODE, message = "인증번호는 6자리 숫자입니다.")
    val code: String,
) {
    fun toCommand(userId: Long): ConfirmPhoneCodeCommand = ConfirmPhoneCodeCommand(userId = userId, phoneNumber = phoneNumber, code = code)
}
