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
    /**
     * 쿨다운을 맨 앞에서 선점한 뒤 발송한다 — 벤더 호출을 쿨다운 안쪽에 넣어야 동시 요청이 두 통을 못 보낸다.
     * 선점 후 어떤 이유로든 발송에 실패하면 쿨다운도 되돌린다(문자가 안 나갔으면 흔적을 남기지 않는다).
     */
    fun sendCode(
        userId: Long,
        rawPhoneNumber: String,
    ): PhoneCodeSentResult {
        val phoneNumber = PhoneNumbers.normalize(rawPhoneNumber)
        if (!codeStore.tryStartCooldown(userId, RESEND_COOLDOWN)) {
            throw BusinessException(UserErrorCode.SMS_RESEND_COOLDOWN)
        }
        try {
            val dateKey = kstToday().format(DATE_KEY)
            enforceDailyLimit(userId, phoneNumber, dateKey)

            val code = codeGenerator.generate()
            codeStore.saveCode(userId, phoneNumber, code, CODE_STORE_TTL)
            dispatchOrRollback(userId, phoneNumber, code, dateKey)
        } catch (e: Exception) {
            codeStore.clearCooldown(userId)
            throw e
        }

        return PhoneCodeSentResult(
            expiresInSeconds = CODE_TTL.seconds.toInt(),
            resendCooldownSeconds = RESEND_COOLDOWN.seconds.toInt(),
        )
    }

    /**
     * 일 상한(유저·번호 이중) — INCR 반환값으로 판정한다(검사와 증가를 쪼개면 동시 발송이 상한을 뚫는다).
     * 번호 기준 상한은 비용 통제만이 아니라 코드 전수 대입의 상한이기도 하다 — 다계정을 허용하므로
     * 유저 기준만으로는 같은 번호에 계정 수만큼 발송할 수 있고, 발송 1건마다 대조 5회가 새로 열린다.
     */
    private fun enforceDailyLimit(
        userId: Long,
        phoneNumber: String,
        dateKey: String,
    ) {
        val expireAt = kstToday().plusDays(1).atStartOfDay(KST).toInstant()
        val counts = codeStore.incrementDailyCounts(userId, phoneNumber, dateKey, expireAt)
        if (counts.userCount > DAILY_SEND_LIMIT || counts.phoneCount > DAILY_SEND_LIMIT) {
            throw BusinessException(UserErrorCode.SMS_DAILY_LIMIT_EXCEEDED)
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
     * 코드 대조 — 코드 불일치와 번호 불일치만 30005 하나로 답한다(구분 비노출: "코드는 맞고 번호가
     * 틀렸다"를 알려주면 6자리 정답 여부가 새어나간다). 만료·시도 초과는 호출자가 이미 아는 사실이라
     * 감출 것이 없고, 재입력이 아니라 재발송이 필요하다는 안내가 필요해 별도 코드로 답한다.
     * 5회째 실패에서 코드를 무효화하며 그 사실을 바로 알린다 — 다음 시도까지 미루면 유저가 정답을 계속 친다.
     */
    fun validateCode(
        userId: Long,
        phoneNumber: String,
        code: String,
    ) {
        val issued = codeStore.findCode(userId) ?: throw BusinessException(UserErrorCode.SMS_CODE_EXPIRED)
        val attempts = codeStore.incrementAttempts(userId, CODE_STORE_TTL)
        if (attempts > MAX_CONFIRM_ATTEMPTS) {
            codeStore.removeCode(userId)
            throw BusinessException(UserErrorCode.SMS_ATTEMPTS_EXCEEDED)
        }
        if (issued.phoneNumber != phoneNumber || issued.code != code) {
            if (attempts >= MAX_CONFIRM_ATTEMPTS) {
                codeStore.removeCode(userId)
                throw BusinessException(UserErrorCode.SMS_ATTEMPTS_EXCEEDED)
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

        /** FE에 동봉하는 코드 수명 — 화면 타이머의 기준값. */
        val CODE_TTL: Duration = Duration.ofMinutes(3)

        /**
         * 저장분에만 얹는 여유. FE 타이머는 응답을 받은 뒤 시작하는데 서버 TTL은 저장 시점부터 도니
         * 벤더 왕복·네트워크 지연만큼 서버가 먼저 만료된다 — 타이머 끝자락의 정상 입력이 튕기지 않게 한다.
         */
        private val CODE_TTL_GRACE: Duration = Duration.ofSeconds(5)

        /** 시도 카운터도 같은 수명을 써야 한다 — 코드보다 먼저 만료되면 5회 제한이 리셋된다. */
        private val CODE_STORE_TTL: Duration = CODE_TTL + CODE_TTL_GRACE
        val RESEND_COOLDOWN: Duration = Duration.ofSeconds(60)
        const val DAILY_SEND_LIMIT = 10
        const val MAX_CONFIRM_ATTEMPTS = 5
    }
}
