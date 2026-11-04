package com.aechak.domain.sns.hashtag

import com.aechak.common.error.BusinessException
import com.aechak.domain.sns.error.SnsErrorCode
import java.text.Normalizer
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/**
 * 계약: 해시태그 표기 정리. 깨지면 표기만 다른 태그가 서로 다른 태그로 저장되거나 쓸 수 없는 글자가 든 태그가 저장된다.
 */
class HashtagTest {
    private fun assertRejected(
        raw: String,
        expected: SnsErrorCode,
    ) {
        val e = assertFailsWith<BusinessException>("입력 [$raw]은 거부해야 한다") { Hashtag.normalize(raw) }
        assertEquals(expected, e.errorCode, "입력 [$raw]의 에러 코드")
    }

    @Test
    fun `분해된 한글은 NFC로 합친다`() {
        val decomposed = Normalizer.normalize("산책", Normalizer.Form.NFD)

        assertEquals("산책", Hashtag.normalize(decomposed))
    }

    @Test
    fun `앞의 #은 하나만 뗀다`() {
        assertEquals("산책", Hashtag.normalize("#산책"))
        assertRejected("##산책", SnsErrorCode.SNS_HASHTAG_INVALID)
    }

    @Test
    fun `앞뒤 공백을 뗀다`() {
        assertEquals("산책", Hashtag.normalize("  #산책\t "))
    }

    @Test
    fun `영문 대소문자만 다른 태그는 같은 이름이 된다`() {
        assertEquals("dog", Hashtag.normalize("#DOG"))
        assertEquals("dog", Hashtag.normalize("dog"))
    }

    @Test
    fun `호환 자모만으로 된 태그를 쓸 수 있다`() {
        assertEquals("ㅋㅋㅋ", Hashtag.normalize("#ㅋㅋㅋ"))
    }

    @Test
    fun `태그는 30자까지 쓸 수 있고 31자면 130501이다`() {
        assertEquals("가".repeat(30), Hashtag.normalize("#" + "가".repeat(30)))
        assertRejected("가".repeat(31), SnsErrorCode.SNS_HASHTAG_TOO_LONG)
    }

    @Test
    fun `공백이나 특수문자가 든 태그는 130500이다`() {
        listOf("산책 가자", "dog!", "강아지-산책", "🐶").forEach { assertRejected(it, SnsErrorCode.SNS_HASHTAG_INVALID) }
    }

    @Test
    fun `빈 값과 #만 있는 값은 130500이다`() {
        listOf("", "   ", "#", " # ").forEach { assertRejected(it, SnsErrorCode.SNS_HASHTAG_INVALID) }
    }
}
