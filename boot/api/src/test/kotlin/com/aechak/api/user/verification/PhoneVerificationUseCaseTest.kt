package com.aechak.api.user.verification

import com.aechak.api.support.IntegrationTestBase
import com.aechak.application.user.user.usecase.UserUseCase
import com.aechak.application.user.verification.service.PhoneVerificationService
import com.aechak.application.user.verification.usecase.PhoneVerificationUseCase
import com.aechak.application.user.verification.usecase.command.ConfirmPhoneCodeCommand
import com.aechak.application.user.verification.usecase.command.SendPhoneCodeCommand
import com.aechak.common.error.BusinessException
import com.aechak.domain.user.error.UserErrorCode
import com.aechak.domain.user.user.User
import org.junit.jupiter.api.BeforeEach
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.data.redis.core.StringRedisTemplate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** 발송 제한(실 Redis INCR)·점유 이전(실 UNIQUE)·마스킹 — 실 배선 검증. 코드는 비운영 고정(000000). */
class PhoneVerificationUseCaseTest : IntegrationTestBase() {
    @Autowired
    private lateinit var phoneVerificationUseCase: PhoneVerificationUseCase

    @Autowired
    private lateinit var userUseCase: UserUseCase

    @Autowired
    private lateinit var redisTemplate: StringRedisTemplate

    private var userSeq = 0

    /** 베이스의 createActiveUser는 프로필 없이 상태만 ACTIVE라 getMe(ACTIVE⇒프로필 불변식)와 충돌 — 온보딩을 거친 시딩. */
    private fun createOnboardedUser(): Long =
        tx.execute {
            val user = User.preRegister()
            user.completeOnboarding("전화인증테스터${++userSeq}")
            em.persist(user)
            em.flush()
            user.id
        }!!

    @BeforeEach
    fun clearSmsKeys() {
        val keys = redisTemplate.keys("sms:*")
        if (keys.isNotEmpty()) {
            redisTemplate.delete(keys)
        }
    }

    @Test
    fun `발송 성공 - 정책값을 동봉하고, 고정 코드로 confirm까지 관통한다`() {
        val userId = createOnboardedUser()

        val sent = phoneVerificationUseCase.sendCode(SendPhoneCodeCommand(userId, "010-1234-5678"))
        assertEquals(180, sent.expiresInSeconds)
        assertEquals(60, sent.resendCooldownSeconds)

        val me = phoneVerificationUseCase.confirm(ConfirmPhoneCodeCommand(userId, "010-1234-5678", "000000"))

        assertTrue(me.isPhoneVerified)
        assertEquals("010-****-5678", me.phoneNumber)
        val stored = tx.execute { em.find(User::class.java, userId).phoneHmac != null }!!
        assertTrue(stored, "phone_hmac(blind index)이 저장돼야 한다")
    }

    @Test
    fun `쿨다운 중 재발송은 30006`() {
        val userId = createOnboardedUser()
        phoneVerificationUseCase.sendCode(SendPhoneCodeCommand(userId, "010-1234-5678"))

        val ex =
            assertFailsWith<BusinessException> {
                phoneVerificationUseCase.sendCode(SendPhoneCodeCommand(userId, "010-1234-5678"))
            }

        assertEquals(UserErrorCode.SMS_RESEND_COOLDOWN, ex.errorCode)
    }

    @Test
    fun `유저 일 상한 초과는 30008`() {
        val userId = createOnboardedUser()
        redisTemplate.opsForValue().set("sms:daily:user:$userId:${todayKst()}", "10")

        val ex =
            assertFailsWith<BusinessException> {
                phoneVerificationUseCase.sendCode(SendPhoneCodeCommand(userId, "010-1234-5678"))
            }

        assertEquals(UserErrorCode.SMS_DAILY_LIMIT_EXCEEDED, ex.errorCode)
        assertFalse(redisTemplate.hasKey("sms:cooldown:$userId"), "거부된 요청은 쿨다운을 남기지 않는다")
    }

    @Test
    fun `번호 일 상한 초과는 유저 상한이 남아 있어도 30008`() {
        val userId = createOnboardedUser()
        redisTemplate.opsForValue().set("sms:daily:phone:01012345678:${todayKst()}", "10")

        val ex =
            assertFailsWith<BusinessException> {
                phoneVerificationUseCase.sendCode(SendPhoneCodeCommand(userId, "010-1234-5678"))
            }

        assertEquals(UserErrorCode.SMS_DAILY_LIMIT_EXCEEDED, ex.errorCode)
    }

    @Test
    fun `confirm - 미발송은 30009, 코드 불일치는 30005`() {
        val userId = createOnboardedUser()

        val noCode =
            assertFailsWith<BusinessException> {
                phoneVerificationUseCase.confirm(ConfirmPhoneCodeCommand(userId, "010-1234-5678", "000000"))
            }
        assertEquals(UserErrorCode.SMS_CODE_EXPIRED, noCode.errorCode)

        phoneVerificationUseCase.sendCode(SendPhoneCodeCommand(userId, "010-1234-5678"))
        val wrongCode =
            assertFailsWith<BusinessException> {
                phoneVerificationUseCase.confirm(ConfirmPhoneCodeCommand(userId, "010-1234-5678", "999999"))
            }
        assertEquals(UserErrorCode.SMS_CODE_INVALID, wrongCode.errorCode)
    }

    @Test
    fun `confirm - 예산을 넘기면 30010으로 소각되고 그 뒤 정답은 30009`() {
        val userId = createOnboardedUser()
        phoneVerificationUseCase.sendCode(SendPhoneCodeCommand(userId, "010-1234-5678"))
        repeat(PhoneVerificationService.MAX_CONFIRM_ATTEMPTS) {
            assertFailsWith<BusinessException> {
                phoneVerificationUseCase.confirm(ConfirmPhoneCodeCommand(userId, "010-1234-5678", "999999"))
            }
        }

        val exceeded =
            assertFailsWith<BusinessException> {
                phoneVerificationUseCase.confirm(ConfirmPhoneCodeCommand(userId, "010-1234-5678", "000000"))
            }
        assertEquals(UserErrorCode.SMS_ATTEMPTS_EXCEEDED, exceeded.errorCode)

        val afterBurn =
            assertFailsWith<BusinessException> {
                phoneVerificationUseCase.confirm(ConfirmPhoneCodeCommand(userId, "010-1234-5678", "000000"))
            }

        assertEquals(UserErrorCode.SMS_CODE_EXPIRED, afterBurn.errorCode)
    }

    @Test
    fun `점유 이전 - 같은 번호를 다른 계정이 인증하면 기존 계정의 인증이 해제된다`() {
        val first = createOnboardedUser()
        phoneVerificationUseCase.sendCode(SendPhoneCodeCommand(first, "010-1234-5678"))
        phoneVerificationUseCase.confirm(ConfirmPhoneCodeCommand(first, "010-1234-5678", "000000"))

        val second = createOnboardedUser()
        phoneVerificationUseCase.sendCode(SendPhoneCodeCommand(second, "010-1234-5678"))
        val me = phoneVerificationUseCase.confirm(ConfirmPhoneCodeCommand(second, "010-1234-5678", "000000"))

        assertTrue(me.isPhoneVerified)
        tx.execute {
            val transferred = em.find(User::class.java, first)
            assertFalse(transferred.isPhoneVerified, "기존 점유 계정은 인증이 해제돼야 한다")
            assertNull(transferred.phoneHmac)
            assertNull(transferred.phoneNumber)
            val owner = em.find(User::class.java, second)
            assertTrue(owner.isPhoneVerified)
            assertNotNull(owner.phoneHmac)
        }
    }

    @Test
    fun `내 정보 조회도 마스킹된 번호를 내려준다`() {
        val userId = createOnboardedUser()
        phoneVerificationUseCase.sendCode(SendPhoneCodeCommand(userId, "010-1234-5678"))
        phoneVerificationUseCase.confirm(ConfirmPhoneCodeCommand(userId, "010-1234-5678", "000000"))

        val me = userUseCase.getMe(userId)

        assertEquals("010-****-5678", me.phoneNumber)
        assertTrue(me.isPhoneVerified)
    }

    private fun todayKst(): String =
        java.time.Instant
            .now()
            .atZone(ZoneId.of("Asia/Seoul"))
            .toLocalDate()
            .format(DateTimeFormatter.BASIC_ISO_DATE)
}
