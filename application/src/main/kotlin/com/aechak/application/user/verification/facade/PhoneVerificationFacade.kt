package com.aechak.application.user.verification.facade

import com.aechak.application.user.user.service.UserService
import com.aechak.application.user.user.usecase.UserUseCase
import com.aechak.application.user.user.usecase.result.UserMeResult
import com.aechak.application.user.verification.service.PhoneVerificationService
import com.aechak.application.user.verification.support.PhoneNumbers
import com.aechak.application.user.verification.support.PhonePiiEncoder
import com.aechak.application.user.verification.usecase.PhoneVerificationUseCase
import com.aechak.application.user.verification.usecase.command.ConfirmPhoneCodeCommand
import com.aechak.application.user.verification.usecase.command.SendPhoneCodeCommand
import com.aechak.application.user.verification.usecase.result.PhoneCodeSentResult
import com.aechak.common.error.BusinessException
import com.aechak.domain.user.error.UserErrorCode
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.stereotype.Service
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.support.TransactionTemplate

/**
 * PhoneVerificationUseCase의 유일한 구현체.
 *
 * confirm은 TransactionTemplate(프로그램적 경계)을 쓴다 — 동시 인증 race의 UNIQUE(phone_hmac) 위반은
 * 커밋 시점 flush에서 터져 선언적 경계 안 catch로는 잡을 수 없다(닉네임 UNIQUE 번역 선례).
 */
@Service
class PhoneVerificationFacade(
    private val verificationService: PhoneVerificationService,
    private val phonePiiEncoder: PhonePiiEncoder,
    private val userService: UserService,
    private val userUseCase: UserUseCase,
    transactionManager: PlatformTransactionManager,
) : PhoneVerificationUseCase {
    private val tx = TransactionTemplate(transactionManager)

    /** 발송은 Redis·SMS만 만진다 — DB 트랜잭션 불요. */
    override fun sendCode(command: SendPhoneCodeCommand): PhoneCodeSentResult =
        verificationService.sendCode(command.userId, command.phoneNumber)

    override fun confirm(command: ConfirmPhoneCodeCommand): UserMeResult {
        val phoneNumber = PhoneNumbers.normalize(command.phoneNumber)
        verificationService.validateCode(command.userId, phoneNumber, command.code)
        val pii = phonePiiEncoder.encode(phoneNumber)

        try {
            tx.execute {
                val user = userService.getById(command.userId)
                val occupant = userService.findPhoneOccupant(pii.phoneHmac)
                if (occupant != null && occupant.id != user.id) {
                    // 점유 이전 — 해제 UPDATE를 먼저 DB로 밀어야 한다. MySQL은 지연 제약이 없어
                    // 신규 세팅이 먼저 나가면 정당한 이전이 UNIQUE(phone_hmac)에 자충한다.
                    occupant.unverifyPhone()
                    userService.flushChanges()
                }
                user.verifyPhone(pii.encrypted, pii.phoneHmac, pii.last4Hmac)
            }
        } catch (e: DataIntegrityViolationException) {
            if (e.message?.contains(PHONE_HMAC_CONSTRAINT) != true &&
                e.cause?.message?.contains(PHONE_HMAC_CONSTRAINT) != true
            ) {
                throw e
            }
            // 동시 인증 race — 같은 번호가 방금 다른 계정에 커밋됐다. 계약에 전용 코드가 없어 30005로 수렴.
            throw BusinessException(UserErrorCode.SMS_CODE_INVALID, e)
        }

        verificationService.consumeCode(command.userId)
        return userUseCase.getMe(command.userId)
    }

    companion object {
        private const val PHONE_HMAC_CONSTRAINT = "uk_users_phone_hmac"
    }
}
