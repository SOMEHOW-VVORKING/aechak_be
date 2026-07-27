package com.aechak.api.user.address.request

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * 연락처 정규화·검증 계약 테스트 — 깨지면 저장 형식이 하이픈 섞인 값으로 오염되거나
 * 정상 번호가 400으로 거절된다. 하이픈은 표기일 뿐 저장은 숫자만이라는 규칙을 못 박는다.
 */
class DeliveryAddressPatternsTest {
    private val contactNumber = Regex(DeliveryAddressPatterns.CONTACT_NUMBER)

    private fun accepts(raw: String) = contactNumber.matches(DeliveryAddressPatterns.normalizeContactNumber(raw))

    @Test
    fun `구분자는 표기라서 걷어낸다`() {
        listOf(
            "010-1234-5678",
            "010 1234 5678",
            "01012345678",
            "010-12345678",
            "0101234-5678",
            "010.1234.5678",
            "(010)1234-5678",
        ).forEach {
            assertEquals("01012345678", DeliveryAddressPatterns.normalizeContactNumber(it), "정규화 실패: $it")
        }
    }

    @Test
    fun `구분자를 어떻게 찍든 같은 번호로 통과한다`() {
        listOf(
            "010-1234-5678",
            "01012345678",
            "010-12345678",
            "0101234-5678",
            "010.1234.5678",
            "(010)1234-5678",
            "016-123-4567",
            "0161234567",
        ).forEach { assertTrue(accepts(it), "통과해야 하는 번호가 거절됨: $it") }
    }

    @Test
    fun `010은 뒤 8자리 고정이라 10자리는 거절한다`() {
        // 010-1234-567처럼 한 자리 빠진 오타를 저장 전에 거른다. 011 등 레거시는 10자리도 실재한다.
        assertFalse(accepts("010-123-4567"), "존재하지 않는 10자리 010이 통과됨")
        assertTrue(accepts("011-123-4567"), "10자리 011은 통과해야 한다")
    }

    @Test
    fun `휴대폰이 아니거나 자릿수가 어긋나면 거절한다`() {
        listOf(
            "02-1234-5678", // 지역번호
            "070-1234-5678", // 인터넷전화
            "010-12-5678", // 자릿수 부족
            "010-1234-56789", // 자릿수 초과
            "+82 10-1234-5678", // 국제표기는 미지원(+ 가 남아 거절)
            "123-456",
        ).forEach { assertFalse(accepts(it), "거절해야 하는 번호가 통과됨: $it") }
    }

    @Test
    fun `구분자가 아닌 문자는 정규화가 지우지 않아 거절된다`() {
        // 걷어내는 대상을 구분자로 한정한 이유 — 비숫자를 전부 지우면 "01|-1234-5678" 같은
        // 오입력이 조용히 다른 사람의 유효한 번호로 바뀐다.
        listOf("01|-1234-5678", "010-1234-567a", "010/1234/5678", "010_1234_5678").forEach {
            assertFalse(accepts(it), "거절해야 하는 오입력이 통과됨: $it")
        }
    }

    @Test
    fun `우편번호는 다섯 자리 숫자만 통과한다`() {
        val zipCode = Regex(DeliveryAddressPatterns.ZIP_CODE)
        assertTrue(zipCode.matches("06236"), "5자리 숫자는 통과해야 한다")
        listOf("0623", "062365", "0623a", "06-236").forEach {
            assertFalse(zipCode.matches(it), "거절해야 하는 우편번호가 통과됨: $it")
        }
    }
}
