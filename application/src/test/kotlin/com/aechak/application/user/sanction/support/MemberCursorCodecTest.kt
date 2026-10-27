package com.aechak.application.user.sanction.support

import com.aechak.application.user.sanction.usecase.query.MemberSearchQuery
import com.aechak.application.user.sanction.usecase.query.MemberStatusFilter
import com.aechak.common.error.BusinessException
import com.aechak.common.error.CommonErrorCode
import java.time.LocalDate
import java.util.Base64
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals

class MemberCursorCodecTest {
    @Test
    fun `인코딩한 커서는 같은 조건 지문과 앵커로 복원된다`() {
        for (filter in MemberStatusFilter.entries) {
            val query = queryOf(filter)
            val decoded = MemberCursorCodec.decode(MemberCursorCodec.encode(query, 4242L))

            assertEquals(MemberCursorCodec.digestOf(query), decoded.filterDigest)
            assertEquals(4242L, decoded.lastUserId)
        }
    }

    @Test
    fun `커서는 URL 안전 문자만 쓴다 — 쿼리 파라미터로 그대로 실린다`() {
        val cursor = MemberCursorCodec.encode(queryOf(MemberStatusFilter.SUSPENDED, keyword = "달&고/양+이"), 1L)

        assertEquals(cursor, cursor.filter { it.isLetterOrDigit() || it == '-' || it == '_' })
    }

    @Test
    fun `지문은 집합을 바꾸는 조건 전부를 따라간다`() {
        val base = queryOf(MemberStatusFilter.ALL)
        val digest = MemberCursorCodec.digestOf(base)

        assertNotEquals(digest, MemberCursorCodec.digestOf(queryOf(MemberStatusFilter.BANNED)))
        assertNotEquals(digest, MemberCursorCodec.digestOf(base.copy(keyword = "달고양이")))
        assertNotEquals(digest, MemberCursorCodec.digestOf(base.copy(joinedFrom = LocalDate.of(2026, 1, 1))))
        assertNotEquals(digest, MemberCursorCodec.digestOf(base.copy(joinedTo = LocalDate.of(2026, 1, 1))))
    }

    @Test
    fun `집합을 바꾸지 않는 조건은 지문을 바꾸지 않는다`() {
        val base = queryOf(MemberStatusFilter.ALL)

        assertEquals(MemberCursorCodec.digestOf(base), MemberCursorCodec.digestOf(base.copy(size = 77)))
        assertEquals(MemberCursorCodec.digestOf(base), MemberCursorCodec.digestOf(base.copy(cursor = "아무거나")))
    }

    @Test
    fun `base64가 아닌 값은 90002로 거부된다`() {
        val exception = assertFailsWith<BusinessException> { MemberCursorCodec.decode("!!not-base64!!") }

        assertEquals(CommonErrorCode.INVALID_CURSOR, exception.errorCode)
    }

    @Test
    fun `다른 목록의 커서는 태그가 달라 거부된다`() {
        val foreign = encodePayload("o:0123456789abcdef:10")

        assertEquals(
            CommonErrorCode.INVALID_CURSOR,
            assertFailsWith<BusinessException> { MemberCursorCodec.decode(foreign) }.errorCode,
        )
    }

    @Test
    fun `지문 형식이 깨졌거나 숫자가 아닌 앵커는 거부된다`() {
        val payloads =
            listOf(
                "m:short:10",
                "m:0123456789abcdef!:10",
                "m:0123456789abcdef:abc",
                "m:0123456789abcdef",
                "m:0123456789abcdef:10:extra",
            )
        for (payload in payloads) {
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

    private fun queryOf(
        filter: MemberStatusFilter,
        keyword: String? = null,
    ): MemberSearchQuery =
        MemberSearchQuery(
            statusFilter = filter,
            keyword = keyword,
            joinedFrom = null,
            joinedTo = null,
            cursor = null,
            size = 20,
        )

    private fun encodePayload(payload: String): String =
        Base64.getUrlEncoder().withoutPadding().encodeToString(payload.toByteArray(Charsets.UTF_8))
}
