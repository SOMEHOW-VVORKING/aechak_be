package com.aechak.infra.persistence.pii

import javax.crypto.spec.SecretKeySpec
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse

/**
 * 계약 — 암호문 프레임([버전 1바이트][본문])과 버전 라우팅.
 * 깨지면 키 로테이션 시 옛 행을 못 읽거나, 잘못된 키로 조용히 복호를 시도하게 된다.
 */
class AesKeyRingTest {
    private val keyV1 = SecretKeySpec("11111111111111111111111111111111".toByteArray(), "AES")
    private val keyV2 = SecretKeySpec("22222222222222222222222222222222".toByteArray(), "AES")

    @Test
    fun `암호화한 것을 그대로 복호하면 원문이 나온다`() {
        val ring = AesKeyRing(mapOf(1.toByte() to keyV1), activeVersion = 1)
        val plain = "01012345678".toByteArray()

        assertContentEquals(plain, ring.decrypt(ring.encrypt(plain)))
    }

    @Test
    fun `암호문 첫 바이트는 활성 키 버전이다`() {
        val ring = AesKeyRing(mapOf(1.toByte() to keyV1), activeVersion = 1)

        assertEquals(1.toByte(), ring.encrypt("x".toByteArray())[0])
    }

    @Test
    fun `같은 평문을 두 번 암호화하면 암호문이 다르다`() {
        // 랜덤 IV의 결과 — 그래서 암호문 컬럼으로는 동등 검색·UNIQUE가 불가능하고 HMAC 컬럼이 따로 필요하다
        val ring = AesKeyRing(mapOf(1.toByte() to keyV1), activeVersion = 1)
        val plain = "01012345678".toByteArray()

        assertFalse(ring.encrypt(plain).contentEquals(ring.encrypt(plain)))
    }

    @Test
    fun `활성 버전이 바뀌어도 옛 버전 암호문을 복호할 수 있다`() {
        val plain = "01012345678".toByteArray()
        val v1Only = AesKeyRing(mapOf(1.toByte() to keyV1), activeVersion = 1)
        val v1Cipher = v1Only.encrypt(plain)

        val rotated = AesKeyRing(mapOf(1.toByte() to keyV1, 2.toByte() to keyV2), activeVersion = 2)

        assertContentEquals(plain, rotated.decrypt(v1Cipher))
        assertEquals(2.toByte(), rotated.encrypt(plain)[0])
    }

    @Test
    fun `미등록 버전의 암호문은 복호를 거부한다`() {
        val ring = AesKeyRing(mapOf(1.toByte() to keyV1), activeVersion = 1)
        val foreign = byteArrayOf(9) + ring.encrypt("x".toByteArray()).drop(1).toByteArray()

        assertFailsWith<IllegalStateException> { ring.decrypt(foreign) }
    }

    @Test
    fun `활성 버전의 키가 없으면 생성 자체가 실패한다`() {
        assertFailsWith<IllegalArgumentException> { AesKeyRing(mapOf(1.toByte() to keyV1), activeVersion = 2) }
    }
}
