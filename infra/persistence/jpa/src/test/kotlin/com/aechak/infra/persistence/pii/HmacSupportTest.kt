package com.aechak.infra.persistence.pii

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

/**
 * 계약 — HMAC의 결정성과 입력 결합("context:value") 형식. 이 형식은 저장된 전 행과 공유하는 영구 계약이다.
 * 골든 벡터가 깨지면 정규화·라벨·결합 규칙 중 무언가가 바뀐 것 — 기존 행의 blind index가 전부 고아가 된다.
 */
class HmacSupportTest {
    private val fixedKey = "0123456789abcdef0123456789abcdef".toByteArray()
    private val hmac = HmacSupport(fixedKey)

    @Test
    fun `골든 벡터 - 고정 키와 입력의 해시는 영원히 같아야 한다`() {
        val actual = hmac.hmac("phone", "01012345678")

        assertEquals(
            "a7b89f2b695ffc2ab5d9df67722c01a120886fc67d308e2f4f3330f6b77dbf43",
            actual.joinToString("") { "%02x".format(it) },
        )
    }

    @Test
    fun `출력은 32바이트다`() {
        // users.phone_hmac binary(32) 컬럼과의 계약
        assertEquals(32, hmac.hmac("phone", "01012345678").size)
    }

    @Test
    fun `context 라벨이 다르면 같은 값도 다른 해시가 나온다`() {
        assertFalse(hmac.hmac("phone", "5678").contentEquals(hmac.hmac("phone-last4", "5678")))
    }

    @Test
    fun `같은 입력이면 항상 같은 해시다`() {
        assertEquals(
            hmac.hmac("phone", "01012345678").toList(),
            hmac.hmac("phone", "01012345678").toList(),
        )
    }
}
