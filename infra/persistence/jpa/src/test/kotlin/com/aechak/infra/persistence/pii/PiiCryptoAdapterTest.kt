package com.aechak.infra.persistence.pii

import com.aechak.application.pii.port.PiiContext
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
    fun `골든 벡터 - context 라벨과 결합 형식은 영원히 같아야 한다`() {
        // 라벨 문자열도 저장된 전 행과 공유하는 계약이다 — 해시값으로 못 박아 조용한 변경을 막는다
        assertEquals(
            "a7b89f2b695ffc2ab5d9df67722c01a120886fc67d308e2f4f3330f6b77dbf43",
            adapter.hmac(PiiContext.PHONE, "01012345678").toHex(),
        )
        assertEquals(
            "3bd9fd5fdaf180d101811eb46556056a0a31bc59a594e1aed2a5fdd17c18e2a0",
            adapter.hmac(PiiContext.PHONE_LAST4, "5678").toHex(),
        )
    }

    @Test
    fun `모든 PiiContext는 서로 다른 해시를 만든다`() {
        // 라벨이 겹치거나 구분자를 품으면 여기서 걸린다 — 라벨 추가 시의 방어선
        val hashes = PiiContext.entries.map { adapter.hmac(it, "5678").toList() }

        assertEquals(PiiContext.entries.size, hashes.toSet().size)
    }

    private fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }
}
