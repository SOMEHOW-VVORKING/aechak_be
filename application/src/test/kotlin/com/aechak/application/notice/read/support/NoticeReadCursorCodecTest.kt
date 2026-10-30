package com.aechak.application.notice.read.support

import com.aechak.common.error.BusinessException
import com.aechak.common.error.CommonErrorCode
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit
import java.util.Base64
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** 계약 테스트 — 라운드트립(고정 여부·마이크로초 보존)과 위조 입력의 400 거절을 고정한다. */
class NoticeReadCursorCodecTest {
    private val epoch: LocalDateTime = LocalDateTime.of(1970, 1, 1, 0, 0)

    @Test
    fun `커서는 고정 여부와 게시 시각과 id를 라운드트립한다`() {
        val publishedAt = LocalDateTime.of(2026, 10, 27, 14, 30, 15, 123_456_000)

        val decoded = NoticeReadCursorCodec.decode(NoticeReadCursorCodec.encode(true, publishedAt, 4242L))

        assertTrue(decoded.lastPinned)
        assertEquals(publishedAt, decoded.lastPublishedAt)
        assertEquals(4242L, decoded.lastNoticeId)
    }

    @Test
    fun `고정이 아닌 행도 그대로 돌아온다`() {
        val publishedAt = LocalDateTime.of(2026, 10, 1, 9, 0)

        val decoded = NoticeReadCursorCodec.decode(NoticeReadCursorCodec.encode(false, publishedAt, 1L))

        assertFalse(decoded.lastPinned)
    }

    @Test
    fun `나노초는 DB 정밀도에 맞춰 마이크로초로 잘린다`() {
        val publishedAt = LocalDateTime.of(2026, 10, 27, 14, 30, 15, 123_456_789)

        val decoded = NoticeReadCursorCodec.decode(NoticeReadCursorCodec.encode(false, publishedAt, 1L))

        assertEquals(publishedAt.withNano(123_456_000), decoded.lastPublishedAt)
    }

    @Test
    fun `Base64가 아닌 커서는 유효하지 않은 커서로 거절된다`() {
        assertInvalidCursor { NoticeReadCursorCodec.decode("!!!not-base64!!!") }
    }

    @Test
    fun `다른 목록의 태그나 필드 수가 다른 커서는 거절된다`() {
        assertInvalidCursor { NoticeReadCursorCodec.decode(crafted("an:1:1700000000000000:1")) }
        assertInvalidCursor { NoticeReadCursorCodec.decode(crafted("nr:1:1700000000000000")) }
        assertInvalidCursor { NoticeReadCursorCodec.decode(crafted("nr:1:1700000000000000:1:9")) }
    }

    /**
     * 고정 여부 필드를 toBoolean()으로 읽으면 아무 문자열이나 false가 되어, 위조 커서가 거절 없이
     * "고정이 아닌 지점"으로 해석된다 — 고정 공지 묶음을 전부 건너뛴 목록이 조용히 나간다.
     */
    @Test
    fun `0이나 1이 아닌 고정 여부를 실은 커서는 거절된다`() {
        assertInvalidCursor { NoticeReadCursorCodec.decode(crafted("nr:true:1700000000000000:1")) }
        assertInvalidCursor { NoticeReadCursorCodec.decode(crafted("nr::1700000000000000:1")) }
        assertInvalidCursor { NoticeReadCursorCodec.decode(crafted("nr:2:1700000000000000:1")) }
    }

    @Test
    fun `0이나 음수 id를 실은 커서는 거절된다`() {
        assertInvalidCursor { NoticeReadCursorCodec.decode(crafted("nr:0:1700000000000000:0")) }
        assertInvalidCursor { NoticeReadCursorCodec.decode(crafted("nr:0:1700000000000000:-1")) }
        assertInvalidCursor { NoticeReadCursorCodec.decode(crafted("nr:0:abc:1")) }
    }

    @Test
    fun `DB가 담을 수 있는 마지막 시각까지는 라운드트립한다`() {
        val dbMax = LocalDateTime.of(9999, 12, 31, 23, 59, 59, 999_999_000)

        val decoded = NoticeReadCursorCodec.decode(NoticeReadCursorCodec.encode(true, dbMax, 1L))

        assertEquals(dbMax, decoded.lastPublishedAt)
    }

    @Test
    fun `DB 범위를 넘는 시각을 실은 커서는 거절된다`() {
        val overflowMicros =
            ChronoUnit.MICROS.between(epoch, LocalDateTime.of(9999, 12, 31, 23, 59, 59, 999_999_000)) + 1

        assertInvalidCursor { NoticeReadCursorCodec.decode(crafted("nr:0:$overflowMicros:1")) }
        assertInvalidCursor { NoticeReadCursorCodec.decode(crafted("nr:0:${Long.MAX_VALUE}:1")) }
    }

    private fun crafted(payload: String): String = Base64.getUrlEncoder().withoutPadding().encodeToString(payload.toByteArray())

    private fun assertInvalidCursor(block: () -> Unit) {
        val exception = runCatching(block).exceptionOrNull()
        assertTrue(exception is BusinessException, "BusinessException이 아니다: $exception")
        assertEquals(CommonErrorCode.INVALID_CURSOR, exception.errorCode)
    }
}
