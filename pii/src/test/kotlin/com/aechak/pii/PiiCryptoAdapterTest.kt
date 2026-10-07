package com.aechak.pii

import com.aechak.application.pii.port.PiiContext
import com.aechak.domain.user.address.DeliveryAddress
import java.util.Base64
import javax.crypto.spec.SecretKeySpec
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** 계약 — 포트 관점의 문자열 왕복. 인코딩(UTF-8)이 양방향에서 일치해야 한글 포함 어떤 평문도 안전하다. */
class PiiCryptoAdapterTest {
    private val adapter =
        PiiCryptoAdapter(
            keyRing = AesKeyRing(mapOf(1.toByte() to SecretKeySpec("11111111111111111111111111111111".toByteArray(), "AES")), 1),
            hmacSupport = HmacSupport("0123456789abcdef0123456789abcdef".toByteArray()),
        )

    @Test
    fun `문자열을 암호화하고 복호하면 원문이 나온다`() {
        assertEquals("01012345678", adapter.decrypt(adapter.encrypt("01012345678")))
    }

    /** 평문 상한을 올리거나 프레임·구현이 바뀌어 암호문이 길어지면 DB 절단으로 조용히 퇴행하므로 경계를 고정한다 */
    @Test
    fun `수령인명 상한 평문의 Base64 암호문이 스냅샷 컬럼 255자를 넘지 않는다`() {
        val cipher = Base64.getEncoder().encodeToString(adapter.encrypt("가".repeat(DeliveryAddress.RECEIVER_NAME_MAX_LENGTH)))
        assertTrue(
            cipher.length <= 255,
            "수령인명 상한 평문의 암호문 ${cipher.length}자가 컬럼 상한을 넘는다",
        )
    }

    @Test
    fun `연락처 암호문은 스냅샷 컬럼 255자 안에 남는다`() {
        val cipher = Base64.getEncoder().encodeToString(adapter.encrypt("01012345678"))
        assertTrue(cipher.length <= 255, "연락처 암호문 ${cipher.length}자가 컬럼 상한을 넘는다")
    }

    @Test
    fun `hmac은 엔진에 그대로 위임한다`() {
        assertEquals(
            HmacSupport("0123456789abcdef0123456789abcdef".toByteArray()).hmac(PiiContext.PHONE, "01012345678").toList(),
            adapter.hmac(PiiContext.PHONE, "01012345678").toList(),
        )
    }
}
