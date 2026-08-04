package com.aechak.application.user.verification.support

import com.aechak.application.pii.PiiCrypto
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class PhoneNumbersTest {
    @Test
    fun `하이픈 등 비숫자를 걷어내고 숫자만 남긴다`() {
        assertEquals("01012345678", PhoneNumbers.normalize("010-1234-5678"))
        assertEquals("0111234567", PhoneNumbers.normalize("011-123-4567"))
    }

    @Test
    fun `형식 위반은 서버 버그로 취급한다 - 웹 경계 검증을 통과했어야 할 입력`() {
        assertFailsWith<IllegalArgumentException> { PhoneNumbers.normalize("02-123-4567") }
        assertFailsWith<IllegalArgumentException> { PhoneNumbers.normalize("010-1234") }
    }
}

/** 계약 — context 라벨·입력 형식은 저장된 전 행과 공유하는 영구 계약이다(회귀 잠금). */
class PhonePiiEncoderTest {
    private val recorded = mutableListOf<Pair<String, String>>()
    private val encoder =
        PhonePiiEncoder(
            object : PiiCrypto {
                override fun encrypt(plain: String): ByteArray = "enc:$plain".toByteArray()

                override fun decrypt(cipher: ByteArray): String = String(cipher).removePrefix("enc:")

                override fun hmac(
                    context: String,
                    value: String,
                ): ByteArray {
                    recorded += context to value
                    return "$context:$value".toByteArray()
                }
            },
        )

    @Test
    fun `정규화된 번호에서 암호문·전체 HMAC·뒷4 HMAC을 파생한다`() {
        val pii = encoder.encode("01012345678")

        assertContentEquals("enc:01012345678".toByteArray(), pii.encrypted)
        assertContentEquals("phone:01012345678".toByteArray(), pii.phoneHmac)
        assertContentEquals("phone-last4:5678".toByteArray(), pii.last4Hmac)
        assertEquals(listOf("phone" to "01012345678", "phone-last4" to "5678"), recorded)
    }
}
