package com.aechak.application.user.sanction.support

import com.aechak.application.user.sanction.usecase.query.MemberStatusFilter
import com.aechak.common.error.BusinessException
import com.aechak.common.error.CommonErrorCode
import java.util.Base64
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class MemberCursorCodecTest {
    @Test
    fun `인코딩한 커서는 같은 필터와 앵커로 복원된다`() {
        for (filter in MemberStatusFilter.entries) {
            val decoded = MemberCursorCodec.decode(MemberCursorCodec.encode(filter, 4242L))

            assertEquals(filter, decoded.filter)
            assertEquals(4242L, decoded.lastUserId)
        }
    }

    @Test
    fun `커서는 URL 안전 문자만 쓴다 — 쿼리 파라미터로 그대로 실린다`() {
        val cursor = MemberCursorCodec.encode(MemberStatusFilter.SUSPENDED, 1L)

        assertEquals(cursor, cursor.filter { it.isLetterOrDigit() || it == '-' || it == '_' })
    }

    @Test
    fun `base64가 아닌 값은 90002로 거부된다`() {
        val exception = assertFailsWith<BusinessException> { MemberCursorCodec.decode("!!not-base64!!") }

        assertEquals(CommonErrorCode.INVALID_CURSOR, exception.errorCode)
    }

    @Test
    fun `다른 목록의 커서는 태그가 달라 거부된다`() {
        val foreign = encodePayload("o:ALL:10")

        assertEquals(
            CommonErrorCode.INVALID_CURSOR,
            assertFailsWith<BusinessException> { MemberCursorCodec.decode(foreign) }.errorCode,
        )
    }

    @Test
    fun `알 수 없는 필터나 숫자가 아닌 앵커는 거부된다`() {
        for (payload in listOf("m:NOT_A_FILTER:10", "m:ALL:abc", "m:ALL", "m:ALL:10:extra")) {
            assertEquals(
                CommonErrorCode.INVALID_CURSOR,
                assertFailsWith<BusinessException> { MemberCursorCodec.decode(encodePayload(payload)) }.errorCode,
                "payload=$payload",
            )
        }
    }

    @Test
    fun `알 수 없는 상태 문자열은 ALL로 본다`() {
        assertEquals(MemberStatusFilter.ALL, MemberStatusFilter.from(null))
        assertEquals(MemberStatusFilter.ALL, MemberStatusFilter.from("오타"))
        assertEquals(MemberStatusFilter.SUSPENDED, MemberStatusFilter.from("suspended"))
        assertEquals(MemberStatusFilter.BANNED, MemberStatusFilter.from(" BANNED "))
    }

    private fun encodePayload(payload: String): String =
        Base64.getUrlEncoder().withoutPadding().encodeToString(payload.toByteArray(Charsets.UTF_8))
}
