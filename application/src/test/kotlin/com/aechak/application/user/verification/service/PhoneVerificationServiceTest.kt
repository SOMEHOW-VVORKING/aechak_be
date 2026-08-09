package com.aechak.application.user.verification.service

import com.aechak.application.user.verification.port.DailySendCounts
import com.aechak.application.user.verification.port.IssuedVerificationCode
import com.aechak.application.user.verification.port.SmsSender
import com.aechak.application.user.verification.port.VerificationCodeStore
import com.aechak.common.error.BusinessException
import com.aechak.domain.user.error.UserErrorCode
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** 발송 제한·실패 보상 — 포트 Fake 기반 유스케이스 로직 검증(TokenServiceTest 결). */
class PhoneVerificationServiceTest {
    private val store = FakeVerificationCodeStore()
    private val smsSender = RecordingSmsSender()
    private val service =
        PhoneVerificationService(
            codeStore = store,
            codeGenerator = { "000000" },
            smsSender = smsSender,
            clock = Clock.fixed(Instant.parse("2026-08-05T03:00:00Z"), ZoneOffset.UTC),
        )

    @Test
    fun `발송 성공 - 코드 저장, 쿨다운 시작, 정책값을 응답에 동봉한다`() {
        val result = service.sendCode(USER_ID, "010-1234-5678")

        assertEquals(IssuedVerificationCode("01012345678", "000000"), store.findCode(USER_ID))
        assertTrue(store.cooldown)
        assertEquals(listOf("01012345678"), smsSender.sentTo)
        assertEquals(180, result.expiresInSeconds)
        assertEquals(60, result.resendCooldownSeconds)
    }

    @Test
    fun `쿨다운 중 재발송은 30006으로 거부된다`() {
        service.sendCode(USER_ID, "010-1234-5678")

        val ex = assertFailsWith<BusinessException> { service.sendCode(USER_ID, "010-1234-5678") }

        assertEquals(UserErrorCode.SMS_RESEND_COOLDOWN, ex.errorCode)
    }

    @Test
    fun `쿨다운은 벤더 호출 전에 선점된다 - 발송 도중 들어온 요청도 막힌다`() {
        // 벤더 호출 시점에 같은 유저의 두 번째 요청이 들어온 상황을 재현한다
        var concurrent: BusinessException? = null
        smsSender.onSend = {
            concurrent = assertFailsWith<BusinessException> { service.sendCode(USER_ID, "010-1234-5678") }
        }

        service.sendCode(USER_ID, "010-1234-5678")

        assertEquals(UserErrorCode.SMS_RESEND_COOLDOWN, concurrent?.errorCode)
        assertEquals(1, smsSender.sentTo.size, "문자는 한 통만 나가야 한다")
    }

    @Test
    fun `유저 일 상한 초과는 30008로 거부된다`() {
        store.userCount = 10

        val ex = assertFailsWith<BusinessException> { service.sendCode(USER_ID, "010-1234-5678") }

        assertEquals(UserErrorCode.SMS_DAILY_LIMIT_EXCEEDED, ex.errorCode)
    }

    @Test
    fun `번호 일 상한 초과는 유저 상한이 남아 있어도 30008로 거부된다`() {
        store.phoneCount = 10

        val ex = assertFailsWith<BusinessException> { service.sendCode(USER_ID, "010-1234-5678") }

        assertEquals(UserErrorCode.SMS_DAILY_LIMIT_EXCEEDED, ex.errorCode)
    }

    @Test
    fun `일 상한으로 거부되면 선점한 쿨다운도 되돌린다`() {
        store.userCount = 10

        assertFailsWith<BusinessException> { service.sendCode(USER_ID, "010-1234-5678") }

        assertFalse(store.cooldown)
    }

    @Test
    fun `벤더 발송 실패 - 30007로 번역하고 코드 소각·상한 미소모, 쿨다운도 남기지 않는다`() {
        smsSender.failing = true

        val ex = assertFailsWith<BusinessException> { service.sendCode(USER_ID, "010-1234-5678") }

        assertEquals(UserErrorCode.SMS_SEND_FAILED, ex.errorCode)
        assertNull(store.findCode(USER_ID))
        assertEquals(0, store.userCount)
        assertEquals(0, store.phoneCount)
        assertFalse(store.cooldown)
    }

    @Test
    fun `confirm - 발송된 코드·번호와 일치하면 통과한다`() {
        service.sendCode(USER_ID, "010-1234-5678")

        service.validateCode(USER_ID, "01012345678", "000000")
    }

    @Test
    fun `confirm - 발송 이력이 없으면(만료 포함) 30009`() {
        val ex = assertFailsWith<BusinessException> { service.validateCode(USER_ID, "01012345678", "000000") }

        assertEquals(UserErrorCode.SMS_CODE_EXPIRED, ex.errorCode)
    }

    @Test
    fun `confirm - 코드 불일치는 30005`() {
        service.sendCode(USER_ID, "010-1234-5678")

        val ex = assertFailsWith<BusinessException> { service.validateCode(USER_ID, "01012345678", "999999") }

        assertEquals(UserErrorCode.SMS_CODE_INVALID, ex.errorCode)
    }

    @Test
    fun `confirm - 발송 번호와 다른 번호는 코드가 맞아도 30005`() {
        service.sendCode(USER_ID, "010-1234-5678")

        val ex = assertFailsWith<BusinessException> { service.validateCode(USER_ID, "01099998888", "000000") }

        assertEquals(UserErrorCode.SMS_CODE_INVALID, ex.errorCode)
    }

    @Test
    fun `confirm - 5회째 실패는 30010이고 그 자리에서 코드를 소각한다`() {
        service.sendCode(USER_ID, "010-1234-5678")
        repeat(4) {
            val ex = assertFailsWith<BusinessException> { service.validateCode(USER_ID, "01012345678", "999999") }
            assertEquals(UserErrorCode.SMS_CODE_INVALID, ex.errorCode)
        }

        val ex = assertFailsWith<BusinessException> { service.validateCode(USER_ID, "01012345678", "999999") }

        assertEquals(UserErrorCode.SMS_ATTEMPTS_EXCEEDED, ex.errorCode)
        assertNull(store.findCode(USER_ID))
    }

    @Test
    fun `confirm - 소각된 뒤에는 정답을 넣어도 30009`() {
        service.sendCode(USER_ID, "010-1234-5678")
        repeat(5) {
            assertFailsWith<BusinessException> { service.validateCode(USER_ID, "01012345678", "999999") }
        }

        val ex = assertFailsWith<BusinessException> { service.validateCode(USER_ID, "01012345678", "000000") }

        assertEquals(UserErrorCode.SMS_CODE_EXPIRED, ex.errorCode)
    }

    private class RecordingSmsSender : SmsSender {
        val sentTo = mutableListOf<String>()
        var failing = false
        var onSend: (() -> Unit)? = null

        override fun send(
            phoneNumber: String,
            message: String,
        ) {
            if (failing) throw IllegalStateException("vendor down")
            onSend?.invoke()
            sentTo += phoneNumber
        }
    }

    private class FakeVerificationCodeStore : VerificationCodeStore {
        private var code: IssuedVerificationCode? = null
        private var attempts = 0L
        var cooldown = false
        var userCount = 0L
        var phoneCount = 0L

        override fun saveCode(
            userId: Long,
            phoneNumber: String,
            code: String,
            ttl: Duration,
        ) {
            this.code = IssuedVerificationCode(phoneNumber, code)
            attempts = 0
        }

        override fun findCode(userId: Long): IssuedVerificationCode? = code

        override fun removeCode(userId: Long) {
            code = null
            attempts = 0
        }

        override fun tryStartCooldown(
            userId: Long,
            ttl: Duration,
        ): Boolean {
            if (cooldown) return false
            cooldown = true
            return true
        }

        override fun clearCooldown(userId: Long) {
            cooldown = false
        }

        override fun incrementDailyCounts(
            userId: Long,
            phoneNumber: String,
            dateKey: String,
            expireAt: Instant,
        ): DailySendCounts {
            userCount += 1
            phoneCount += 1
            return DailySendCounts(userCount, phoneCount)
        }

        override fun rollbackDailyCounts(
            userId: Long,
            phoneNumber: String,
            dateKey: String,
        ) {
            userCount -= 1
            phoneCount -= 1
        }

        override fun incrementAttempts(
            userId: Long,
            ttl: Duration,
        ): Long = ++attempts
    }

    companion object {
        private const val USER_ID = 1L
    }
}
