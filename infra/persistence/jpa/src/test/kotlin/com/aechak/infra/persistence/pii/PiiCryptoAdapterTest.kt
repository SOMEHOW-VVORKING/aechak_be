package com.aechak.infra.persistence.pii

import javax.crypto.spec.SecretKeySpec
import kotlin.test.Test
import kotlin.test.assertEquals

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

    @Test
    fun `hmac은 엔진과 동일한 값을 위임 반환한다`() {
        assertEquals(
            HmacSupport("0123456789abcdef0123456789abcdef".toByteArray()).hmac("phone", "01012345678").toList(),
            adapter.hmac("phone", "01012345678").toList(),
        )
    }
}
