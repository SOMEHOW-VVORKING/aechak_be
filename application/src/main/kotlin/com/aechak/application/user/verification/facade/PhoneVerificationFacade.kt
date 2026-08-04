package com.aechak.application.user.verification.facade

import com.aechak.application.user.verification.service.PhoneVerificationService
import com.aechak.application.user.verification.usecase.PhoneVerificationUseCase
import com.aechak.application.user.verification.usecase.command.SendPhoneCodeCommand
import com.aechak.application.user.verification.usecase.result.PhoneCodeSentResult
import org.springframework.stereotype.Service

/** PhoneVerificationUseCase의 유일한 구현체. */
@Service
class PhoneVerificationFacade(
    private val verificationService: PhoneVerificationService,
) : PhoneVerificationUseCase {
    /** 발송은 Redis·SMS만 만진다 — DB 트랜잭션 불요. */
    override fun sendCode(command: SendPhoneCodeCommand): PhoneCodeSentResult =
        verificationService.sendCode(command.userId, command.phoneNumber)
}
