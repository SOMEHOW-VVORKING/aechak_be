package com.aechak.domain.user.user

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * 계약 — 전화 인증 상태는 다섯 필드가 한 몸으로 움직인다. 깨지면 반쪽 인증 상태(해시만 있고 미인증 등)가 생긴다.
 */
class UserPhoneVerificationTest {
    private val encrypted = byteArrayOf(1, 10, 20)
    private val phoneHmac = ByteArray(32) { 1 }
    private val last4Hmac = ByteArray(32) { 2 }

    private fun activeUser(): User = User.preRegister().apply { completeOnboarding("코코집사") }

    @Test
    fun `verifyPhone은 암호문·해시 2종·인증 상태를 일괄 세팅한다`() {
        val user = activeUser()

        user.verifyPhone(encrypted, phoneHmac, last4Hmac)

        assertContentEquals(encrypted, user.phoneNumber)
        assertContentEquals(phoneHmac, user.phoneHmac)
        assertContentEquals(last4Hmac, user.phoneLast4Hmac)
        assertTrue(user.isPhoneVerified)
        assertNotNull(user.phoneVerifiedAt)
    }

    @Test
    fun `ACTIVE가 아니면 verifyPhone이 거부된다`() {
        val pending = User.preRegister()

        assertFailsWith<IllegalStateException> { pending.verifyPhone(encrypted, phoneHmac, last4Hmac) }
    }

    @Test
    fun `unverifyPhone은 전화 관련 상태를 일괄 클리어한다`() {
        val user = activeUser()
        user.verifyPhone(encrypted, phoneHmac, last4Hmac)

        user.unverifyPhone()

        assertNull(user.phoneNumber)
        assertNull(user.phoneHmac)
        assertNull(user.phoneLast4Hmac)
        assertFalse(user.isPhoneVerified)
        assertNull(user.phoneVerifiedAt)
    }
}
