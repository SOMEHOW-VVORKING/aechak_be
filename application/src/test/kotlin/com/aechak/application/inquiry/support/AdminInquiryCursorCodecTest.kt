package com.aechak.application.inquiry.support

import com.aechak.common.error.BusinessException
import com.aechak.common.error.CommonErrorCode
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit
import java.util.Base64
import kotlin.test.Test
import kotlin.test.assertEquals

/** 계약 테스트 — 라운드트립(마이크로초 보존)과 위조 입력의 400 거절을 고정한다. */
class AdminInquiryCursorCodecTest {
    private val epoch: LocalDateTime = LocalDateTime.of(1970, 1, 1, 0, 0)

    @Test
    fun `커서는 접수 시각과 id를 마이크로초까지 라운드트립한다`() {
        val createdAt = LocalDateTime.of(2026, 10, 27, 14, 30, 15, 123_456_000)

        val decoded = AdminInquiryCursorCodec.decode(AdminInquiryCursorCodec.encode(createdAt, 4242L))

        assertEquals(createdAt, decoded.lastCreatedAt)
        assertEquals(4242L, decoded.lastInquiryId)
    }

    @Test
    fun `나노초는 DB 정밀도에 맞춰 마이크로초로 잘린다`() {
        val createdAt = LocalDateTime.of(2026, 10, 27, 14, 30, 15, 123_456_789)

        val decoded = AdminInquiryCursorCodec.decode(AdminInquiryCursorCodec.encode(createdAt, 1L))

        assertEquals(createdAt.withNano(123_456_000), decoded.lastCreatedAt)
    }

    @Test
    fun `Base64가 아닌 커서는 유효하지 않은 커서로 거절된다`() {
        assertInvalidCursor { AdminInquiryCursorCodec.decode("!!!not-base64!!!") }
    }

    @Test
    fun `다른 목록의 태그나 필드 수가 다른 커서는 거절된다`() {
        assertInvalidCursor { AdminInquiryCursorCodec.decode(crafted("lk:1700000000000000:1")) }
        assertInvalidCursor { AdminInquiryCursorCodec.decode(crafted("ai:1700000000000000")) }
    }

    @Test
    fun `0이나 음수 id를 실은 커서는 거절된다`() {
        assertInvalidCursor { AdminInquiryCursorCodec.decode(crafted("ai:1700000000000000:0")) }
        assertInvalidCursor { AdminInquiryCursorCodec.decode(crafted("ai:1700000000000000:-1")) }
        assertInvalidCursor { AdminInquiryCursorCodec.decode(crafted("ai:abc:1")) }
    }

    @Test
    fun `DB가 담을 수 있는 마지막 시각까지는 라운드트립한다`() {
        val dbMax = LocalDateTime.of(9999, 12, 31, 23, 59, 59, 999_999_000)

        val decoded = AdminInquiryCursorCodec.decode(AdminInquiryCursorCodec.encode(dbMax, 1L))

        assertEquals(dbMax, decoded.lastCreatedAt)
    }

    @Test
    fun `DB 범위를 넘는 시각을 실은 커서는 거절된다`() {
        // LocalDateTime으로는 멀쩡한 값이라 양수 검사만으로는 통과한다 — DATETIME 상한을 넘기면 조회로 새면 안 된다
        val overflowMicros = ChronoUnit.MICROS.between(epoch, LocalDateTime.of(9999, 12, 31, 23, 59, 59, 999_999_000)) + 1

        assertInvalidCursor { AdminInquiryCursorCodec.decode(crafted("ai:$overflowMicros:1")) }
        assertInvalidCursor { AdminInquiryCursorCodec.decode(crafted("ai:${Long.MAX_VALUE}:1")) }
    }

    private fun crafted(payload: String): String = Base64.getUrlEncoder().withoutPadding().encodeToString(payload.toByteArray())

    private fun assertInvalidCursor(block: () -> Unit) {
        try {
            block()
            throw AssertionError("INVALID_CURSOR가 발생해야 한다")
        } catch (e: BusinessException) {
            assertEquals(CommonErrorCode.INVALID_CURSOR, e.errorCode)
        }
    }
}
