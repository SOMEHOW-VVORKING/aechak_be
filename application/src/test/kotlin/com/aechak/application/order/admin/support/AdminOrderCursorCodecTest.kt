package com.aechak.application.order.admin.support

import com.aechak.application.order.admin.port.AdminOrderCursor
import com.aechak.common.error.BusinessException
import com.aechak.common.error.CommonErrorCode
import java.time.LocalDateTime
import java.util.Base64
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals

/**
 * 어드민 주문 목록 커서 코덱 단위 테스트.
 * 마이크로초까지 왕복해야 같은 초에 들어온 주문이 커서 경계에서 섞이지 않는다.
 */
class AdminOrderCursorCodecTest {
    @Test
    fun `커서를 왕복하면 주문 시각과 주문 id가 보존된다`() {
        val anchor = AdminOrderCursor(LocalDateTime.of(2026, 10, 28, 9, 30, 15, 123_456_000), 42L)

        val decoded = AdminOrderCursorCodec.decode(AdminOrderCursorCodec.encode(anchor))

        assertEquals(anchor.lastOrderedAt, decoded.lastOrderedAt, "주문 시각이 마이크로초까지 보존되어야 한다")
        assertEquals(42L, decoded.lastOrderId, "주문 id가 보존되어야 한다")
    }

    @Test
    fun `같은 초의 두 앵커는 서로 다른 커서가 된다`() {
        val earlier = AdminOrderCursor(LocalDateTime.of(2026, 10, 28, 9, 30, 15, 1_000), 1L)
        val later = AdminOrderCursor(LocalDateTime.of(2026, 10, 28, 9, 30, 15, 2_000), 1L)

        assertNotEquals(
            AdminOrderCursorCodec.encode(earlier),
            AdminOrderCursorCodec.encode(later),
            "밀리초 아래가 뭉개지면 같은 초의 주문이 한 페이지에 두 번 나오거나 건너뛰어진다",
        )
    }

    @Test
    fun `나노초는 마이크로초로 끊어 왕복한다`() {
        val anchor = AdminOrderCursor(LocalDateTime.of(2026, 10, 28, 9, 30, 15, 123_456_789), 7L)

        val decoded = AdminOrderCursorCodec.decode(AdminOrderCursorCodec.encode(anchor))

        assertEquals(
            LocalDateTime.of(2026, 10, 28, 9, 30, 15, 123_456_000),
            decoded.lastOrderedAt,
            "DB DATETIME(6) 정밀도를 넘는 자리는 버려야 커서가 DB 값과 같은 경계를 가리킨다",
        )
    }

    @Test
    fun `깨진 커서는 INVALID_CURSOR로 거절한다`() {
        assertCursorRejected { AdminOrderCursorCodec.decode("%%%broken%%%") }
    }

    @Test
    fun `태그가 다른 커서는 INVALID_CURSOR로 거절한다`() {
        assertCursorRejected { AdminOrderCursorCodec.decode(encoded("xx:1:1")) }
    }

    @Test
    fun `토큰 수가 다른 커서는 INVALID_CURSOR로 거절한다`() {
        assertCursorRejected { AdminOrderCursorCodec.decode(encoded("ao:1")) }
    }

    @Test
    fun `숫자가 아닌 앵커는 INVALID_CURSOR로 거절한다`() {
        assertCursorRejected { AdminOrderCursorCodec.decode(encoded("ao:abc:1")) }
    }

    @Test
    fun `0 이하의 주문 id는 INVALID_CURSOR로 거절한다`() {
        assertCursorRejected { AdminOrderCursorCodec.decode(encoded("ao:1:0")) }
    }

    private fun encoded(payload: String): String =
        Base64.getUrlEncoder().withoutPadding().encodeToString(payload.toByteArray(Charsets.UTF_8))

    private fun assertCursorRejected(block: () -> Unit) {
        val exception = assertFailsWith<BusinessException>(block = block)
        assertEquals(CommonErrorCode.INVALID_CURSOR, exception.errorCode, "커서 훼손은 전부 INVALID_CURSOR로 접어야 한다")
    }
}
