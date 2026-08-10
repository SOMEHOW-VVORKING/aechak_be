package com.aechak.pii

import com.aechak.application.pii.port.PiiContext
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

/**
 * 계약 — HMAC의 결정성과 입력 결합("라벨:값") 형식. 이 형식은 저장된 전 행과 공유하는 영구 계약이다.
 * 골든 벡터가 깨지면 정규화·라벨·결합 규칙 중 무언가가 바뀐 것 — 기존 행의 blind index가 전부 고아가 된다.
 */
class HmacSupportTest {
    private val fixedKey = "0123456789abcdef0123456789abcdef".toByteArray()
    private val hmac = HmacSupport(fixedKey)

    @Test
    fun `골든 벡터 - 고정 키와 입력의 해시는 영원히 같아야 한다`() {
        // 라벨 문자열도 계약이라 해시값으로 함께 못 박는다
        assertEquals(
            "a7b89f2b695ffc2ab5d9df67722c01a120886fc67d308e2f4f3330f6b77dbf43",
            hmac.hmac(PiiContext.PHONE, "01012345678").toHex(),
        )
        assertEquals(
            "3bd9fd5fdaf180d101811eb46556056a0a31bc59a594e1aed2a5fdd17c18e2a0",
            hmac.hmac(PiiContext.PHONE_LAST4, "5678").toHex(),
        )
    }

    @Test
    fun `출력은 32바이트다`() {
        // users.phone_hmac binary(32) 컬럼과의 계약
        assertEquals(32, hmac.hmac(PiiContext.PHONE, "01012345678").size)
    }

    @Test
    fun `라벨이 다르면 같은 값도 다른 해시가 나온다`() {
        assertFalse(hmac.hmac(PiiContext.PHONE, "5678").contentEquals(hmac.hmac(PiiContext.PHONE_LAST4, "5678")))
    }

    @Test
    fun `모든 라벨은 결합 구분자를 포함하지 않는다`() {
        // 포함하면 ("account","123:456")과 ("account:123","456")이 같은 입력으로 뭉갠다
        PiiContext.entries.forEach { assertFalse(':' in it.label, "구분자를 포함한 라벨: ${it.label}") }
    }

    @Test
    fun `값의 구분자는 경계를 흔들지 않는다`() {
        // 값은 마지막 필드 — 첫 구분자 이후를 통째로 가져가므로 쪼개질 일이 없다
        assertFalse(
            hmac.hmac(PiiContext.PHONE, "010:1234").contentEquals(hmac.hmac(PiiContext.PHONE, "0101234")),
        )
    }

    @Test
    fun `같은 입력이면 항상 같은 해시다`() {
        assertEquals(
            hmac.hmac(PiiContext.PHONE, "01012345678").toList(),
            hmac.hmac(PiiContext.PHONE, "01012345678").toList(),
        )
    }

    private fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }
}
