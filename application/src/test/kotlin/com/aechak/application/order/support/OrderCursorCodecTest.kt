package com.aechak.application.order.support

import com.aechak.application.order.port.OrderStatusFilter
import com.aechak.common.error.BusinessException
import com.aechak.common.error.CommonErrorCode
import java.util.Base64
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/** 주문 목록 커서 코덱 단위 테스트. 커서에 봉인한 필터가 왕복에서 보존되는지와 훼손된 커서를 전부 INVALID_CURSOR로 접는지를 고정함. */
class OrderCursorCodecTest {
    @Test
    fun `커서를 왕복하면 필터와 lastId가 보존된다`() {
        val encoded = OrderCursorCodec.encode(OrderStatusFilter.ONGOING, lastId = 42L)

        val decoded = OrderCursorCodec.decode(encoded)

        assertEquals(OrderStatusFilter.ONGOING, decoded.filter, "필터가 보존되어야 한다")
        assertEquals(42L, decoded.lastId, "lastId가 보존되어야 한다")
    }

    @Test
    fun `페이지 중간에 필터를 바꾼 커서는 대조에서 걸리도록 필터를 그대로 실어 준다`() {
        val encoded = OrderCursorCodec.encode(OrderStatusFilter.CANCELLED, lastId = 7L)

        assertEquals(OrderStatusFilter.CANCELLED, OrderCursorCodec.decode(encoded).filter, "인코딩한 필터가 그대로 나와야 한다")
    }

    @Test
    fun `깨진 커서는 INVALID_CURSOR로 거절한다`() {
        assertCursorRejected { OrderCursorCodec.decode("%%%broken%%%") }
    }

    @Test
    fun `토큰 수가 다른 커서는 INVALID_CURSOR로 거절한다`() {
        val malformed = encoded("o:ONGOING".toByteArray())

        assertCursorRejected { OrderCursorCodec.decode(malformed) }
    }

    @Test
    fun `알 수 없는 필터 이름은 INVALID_CURSOR로 거절한다`() {
        val malformed = encoded("o:NOPE:1".toByteArray())

        assertCursorRejected { OrderCursorCodec.decode(malformed) }
    }

    @Test
    fun `lastId가 숫자가 아니면 INVALID_CURSOR로 거절한다`() {
        val malformed = encoded("o:ALL:abc".toByteArray())

        assertCursorRejected { OrderCursorCodec.decode(malformed) }
    }

    private fun encoded(payload: ByteArray): String = Base64.getUrlEncoder().withoutPadding().encodeToString(payload)

    private fun assertCursorRejected(block: () -> Unit) {
        val e = assertFailsWith<BusinessException>(block = block)
        assertEquals(CommonErrorCode.INVALID_CURSOR, e.errorCode, "커서 오류는 INVALID_CURSOR여야 한다")
    }
}
