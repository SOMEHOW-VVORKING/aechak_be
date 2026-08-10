package com.aechak.api.user.verification

import com.aechak.api.user.user.response.UserMeResponse
import com.aechak.api.user.verification.request.ConfirmPhoneCodeRequest
import com.aechak.api.user.verification.request.SendPhoneCodeRequest
import com.aechak.api.user.verification.response.PhoneCodeSentResponse
import com.aechak.application.user.verification.usecase.PhoneVerificationUseCase
import com.aechak.webcommon.response.ApiResponse
import com.aechak.websecurity.authentication.AuthPrincipal
import jakarta.validation.Valid
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/** 휴대폰 점유 인증 API — ACTIVE 전용(PENDING은 UserStatusFilter가 20006으로 차단). */
@RestController
@RequestMapping("/users/me/phone") // 접두(api.base-path)는 WebConfig가 일괄 부착
class PhoneVerificationController(
    private val phoneVerificationUseCase: PhoneVerificationUseCase,
) {
    /** 인증번호 발송(재발송 = 재호출) — 응답 값이 FE 타이머의 원천. */
    @PostMapping("/verifications")
    fun sendCode(
        @Valid @RequestBody request: SendPhoneCodeRequest,
        @AuthenticationPrincipal principal: AuthPrincipal,
    ): ResponseEntity<ApiResponse<PhoneCodeSentResponse>> =
        ResponseEntity.ok(
            ApiResponse.of(PhoneCodeSentResponse.from(phoneVerificationUseCase.sendCode(request.toCommand(principal.userId)))),
        )

    /** 인증 확인 — 성공 시 변경 후 내 정보 반환(phoneNumber는 서버 마스킹 표시용). */
    @PostMapping("/verifications/confirm")
    fun confirm(
        @Valid @RequestBody request: ConfirmPhoneCodeRequest,
        @AuthenticationPrincipal principal: AuthPrincipal,
    ): ResponseEntity<ApiResponse<UserMeResponse>> =
        ResponseEntity.ok(
            ApiResponse.of(UserMeResponse.from(phoneVerificationUseCase.confirm(request.toCommand(principal.userId)))),
        )
}
