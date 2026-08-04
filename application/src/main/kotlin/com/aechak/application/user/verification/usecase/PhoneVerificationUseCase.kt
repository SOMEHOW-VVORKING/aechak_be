package com.aechak.application.user.verification.usecase

import com.aechak.application.user.verification.usecase.command.SendPhoneCodeCommand
import com.aechak.application.user.verification.usecase.result.PhoneCodeSentResult

/**
 * 전화 인증 진입점 — 산출물이 users의 영속 상태(is_phone_verified·전화번호)라 user 도메인 소속이다.
 * 외부(Controller 등)는 이 인터페이스만 import한다 — Facade/Service 직접 호출 금지.
 */
interface PhoneVerificationUseCase {
    /**
     * 인증번호 발송(재발송 = 재호출) — 쿨다운·일 상한(유저·번호 이중) 초과는 30006, 벤더 실패는 30007.
     * 응답의 만료·쿨다운 값이 FE 타이머의 원천이다(클라이언트 하드코딩 금지).
     */
    fun sendCode(command: SendPhoneCodeCommand): PhoneCodeSentResult
}
