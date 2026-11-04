package com.aechak.application.sns.support

import com.aechak.common.error.BusinessException
import com.aechak.common.error.CommonErrorCode
import java.time.Instant
import java.time.LocalDateTime
import java.util.Base64
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/**
 * 계약: 피드 커서가 기준 시각과 마지막 게시물 공개 ID를 마이크로초까지 왕복하고 다른 목록의 커서와 깨진 커서를 90002로 거절하는지.
 * 깨지면 다음 페이지가 다른 기준 시각으로 잘리거나 다른 목록의 커서로 페이지를 넘긴다.
 */
class SnsFeedCursorCodecTest {
    private val lastPostPublicId = "01JG3A5C7E9G1J3K5M7P9R1T3V"

    @Test
    fun `탐색 커서를 왕복하면 기준 시각과 마지막 게시물 공개 ID가 보존된다`() {
        val anchor = LocalDateTime.of(2026, 11, 5, 9, 30, 15, 123_456_000)

        val decoded = SnsFeedCursorCodec.decodeExplore(SnsFeedCursorCodec.encodeExplore(anchor, lastPostPublicId))

        assertEquals(anchor, decoded.anchor, "기준 시각이 보존되어야 한다")
        assertEquals(lastPostPublicId, decoded.lastPostPublicId, "마지막 게시물 공개 ID가 보존되어야 한다")
    }

    @Test
    fun `시각은 UTC 기준 epoch 마이크로초로 담고 마이크로초 아래는 버린다`() {
        val anchor = LocalDateTime.of(2026, 11, 5, 9, 30, 15, 123_456_789)
        val expectedMicros = Instant.parse("2026-11-05T09:30:15.123456Z").let { it.epochSecond * 1_000_000 + it.nano / 1_000 }

        val cursor = SnsFeedCursorCodec.encodeExplore(anchor, lastPostPublicId)

        assertEquals("ex:$expectedMicros:$lastPostPublicId", decodeRaw(cursor), "시각은 UTC 기준 epoch 마이크로초여야 한다")
        assertEquals(
            LocalDateTime.of(2026, 11, 5, 9, 30, 15, 123_456_000),
            SnsFeedCursorCodec.decodeExplore(cursor).anchor,
            "마이크로초까지 보존하고 그 아래는 버려야 한다",
        )
    }

    @Test
    fun `다른 태그의 커서는 거절한다`() {
        assertCursorRejected { SnsFeedCursorCodec.decodeExplore(encodeRaw("fl:1:2:$lastPostPublicId")) }
        assertCursorRejected { SnsFeedCursorCodec.decodeExplore(encodeRaw("xx:1:$lastPostPublicId")) }
    }

    @Test
    fun `깨진 base64 커서는 거절한다`() {
        assertCursorRejected { SnsFeedCursorCodec.decodeExplore("%%%broken%%%") }
    }

    @Test
    fun `칸 수, 시각, 공개 ID가 형식에 맞지 않는 커서는 거절한다`() {
        assertCursorRejected { SnsFeedCursorCodec.decodeExplore(encodeRaw("ex:1")) }
        assertCursorRejected { SnsFeedCursorCodec.decodeExplore(encodeRaw("ex:1:$lastPostPublicId:x")) }
        assertCursorRejected { SnsFeedCursorCodec.decodeExplore(encodeRaw("ex:not-a-number:$lastPostPublicId")) }
        assertCursorRejected { SnsFeedCursorCodec.decodeExplore(encodeRaw("ex:0:$lastPostPublicId")) }
        assertCursorRejected { SnsFeedCursorCodec.decodeExplore(encodeRaw("ex:1:")) }
    }

    private fun encodeRaw(payload: String): String =
        Base64
            .getUrlEncoder()
            .withoutPadding()
            .encodeToString(payload.toByteArray(Charsets.UTF_8))

    private fun decodeRaw(cursor: String): String = String(Base64.getUrlDecoder().decode(cursor), Charsets.UTF_8)

    private fun assertCursorRejected(block: () -> Unit) {
        val exception = assertFailsWith<BusinessException>(block = block)
        assertEquals(CommonErrorCode.INVALID_CURSOR, exception.errorCode, "거절한 커서는 90002로 실패해야 한다")
    }
}
