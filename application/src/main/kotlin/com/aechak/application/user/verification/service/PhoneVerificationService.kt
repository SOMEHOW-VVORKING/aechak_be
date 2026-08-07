package com.aechak.application.user.verification.service

import com.aechak.application.user.verification.port.SmsSender
import com.aechak.application.user.verification.port.VerificationCodeStore
import com.aechak.application.user.verification.support.PhoneNumbers
import com.aechak.application.user.verification.support.VerificationCodeGenerator
import com.aechak.application.user.verification.usecase.result.PhoneCodeSentResult
import com.aechak.common.error.BusinessException
import com.aechak.domain.user.error.UserErrorCode
import org.springframework.stereotype.Service
import java.time.Clock
import java.time.Duration
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * 전화 인증 비즈니스 로직 — 발송 제한·코드 수명 정책의 단일 소유자.
 * 정책 수치는 응답(PhoneCodeSentResult)으로 FE에 동봉된다 — 여기 바꾸면 FE 타이머도 따라온다.
 */
@Service
class PhoneVerificationService(
    private val codeStore: VerificationCodeStore,
    private val codeGenerator: VerificationCodeGenerator,
    private val smsSender: SmsSender,
    private val clock: Clock,
) {
    fun sendCode(
        userId: Long,
        rawPhoneNumber: String,
    ): PhoneCodeSentResult {
        val phoneNumber = PhoneNumbers.normalize(rawPhoneNumber)
        if (codeStore.isInCooldown(userId)) {
            throw BusinessException(UserErrorCode.SMS_RATE_LIMITED)
        }
        val dateKey = kstToday().format(DATE_KEY)
        enforceDailyLimit(userId, phoneNumber, dateKey)

        val code = codeGenerator.generate()
        codeStore.saveCode(userId, phoneNumber, code, CODE_TTL)
        dispatchOrRollback(userId, phoneNumber, code, dateKey)
        codeStore.startCooldown(userId, RESEND_COOLDOWN)

        return PhoneCodeSentResult(
            expiresInSeconds = CODE_TTL.seconds.toInt(),
            resendCooldownSeconds = RESEND_COOLDOWN.seconds.toInt(),
        )
    }

    /** 일 상한(유저·번호 이중) — INCR 반환값으로 판정한다(검사와 증가를 쪼개면 동시 발송이 상한을 뚫는다). */
    private fun enforceDailyLimit(
        userId: Long,
        phoneNumber: String,
        dateKey: String,
    ) {
        val expireAt = kstToday().plusDays(1).atStartOfDay(KST).toInstant()
        val counts = codeStore.incrementDailyCounts(userId, phoneNumber, dateKey, expireAt)
        if (counts.userCount > DAILY_SEND_LIMIT || counts.phoneCount > DAILY_SEND_LIMIT) {
            throw BusinessException(UserErrorCode.SMS_RATE_LIMITED)
        }
    }

    /** 발송 실패는 흔적을 남기지 않는다 — 코드 소각 + 상한 미소모(발송 때와 같은 dateKey로 롤백해야 자정 경계에서 엉뚱한 버킷을 안 깎는다). */
    private fun dispatchOrRollback(
        userId: Long,
        phoneNumber: String,
        code: String,
        dateKey: String,
    ) {
        try {
            smsSender.send(phoneNumber, "[애착] 인증번호 [$code]를 입력해 주세요.")
        } catch (e: Exception) {
            codeStore.removeCode(userId)
            codeStore.rollbackDailyCounts(userId, phoneNumber, dateKey)
            throw BusinessException(UserErrorCode.SMS_SEND_FAILED, e)
        }
    }

    /**
     * 코드 대조 — 만료·번호 불일치·코드 불일치·시도 초과를 전부 30005 하나로 답한다(구분 비노출: 어느 쪽이
     * 틀렸는지 알려주면 공격자에게 힌트가 된다). 5회 실패 시 코드를 무효화해 재발송을 강제한다.
     */
    fun validateCode(
        userId: Long,
        phoneNumber: String,
        code: String,
    ) {
        val issued = codeStore.findCode(userId) ?: throw BusinessException(UserErrorCode.SMS_CODE_INVALID)
        val attempts = codeStore.incrementAttempts(userId, CODE_TTL)
        if (attempts > MAX_CONFIRM_ATTEMPTS) {
            codeStore.removeCode(userId)
            throw BusinessException(UserErrorCode.SMS_CODE_INVALID)
        }
        if (issued.phoneNumber != phoneNumber || issued.code != code) {
            if (attempts >= MAX_CONFIRM_ATTEMPTS) {
                codeStore.removeCode(userId)
            }
            throw BusinessException(UserErrorCode.SMS_CODE_INVALID)
        }
    }

    /** 코드 소각 — 인증이 커밋된 뒤에만 부른다(트랜잭션 실패 시 재시도 여지 보존). */
    fun consumeCode(userId: Long) = codeStore.removeCode(userId)

    private fun kstToday() = clock.instant().atZone(KST).toLocalDate()

    companion object {
        /** 일 상한 버킷은 서버 TZ와 무관하게 KST 자정 기준 — 운영 정책(한국 서비스)의 날짜 감각과 일치시킨다. */
        private val KST = ZoneId.of("Asia/Seoul")
        private val DATE_KEY = DateTimeFormatter.BASIC_ISO_DATE
        val CODE_TTL: Duration = Duration.ofMinutes(3)
        val RESEND_COOLDOWN: Duration = Duration.ofSeconds(60)
        const val DAILY_SEND_LIMIT = 10
        const val MAX_CONFIRM_ATTEMPTS = 5
    }
}
