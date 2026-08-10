package com.aechak.message

import java.time.Instant
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals

/** [단위] 메시지 계약 규칙. 멱등키가 생성자 프로퍼티라 copy·반복 읽기에 안정적임을 고정 */
class MessageContractTest {
    /** 계약이 권하는 모양 그대로의 표본. 멱등키·시각을 생성자에서 받는다 */
    private data class SampleMessage(
        val hello: String,
        override val aggregateId: String = "test",
        override val eventId: String = UUID.randomUUID().toString(),
        override val occurredAt: Instant = Instant.now(),
    ) : GuaranteedMessage {
        override val aggregateType: String = "order"
    }

    @Test
    fun `copy를 해도 멱등키는 유지된다`() {
        val origin = SampleMessage("a")
        val copied = origin.copy(hello = "b")

        assertEquals(origin.eventId, copied.eventId, "키가 본문 프로퍼티였다면 copy가 키를 새로 뽑아 같은 사건이 다른 사건으로 둔갑한다")
    }

    @Test
    fun `멱등키는 몇 번을 읽어도 같은 값이다`() {
        val message = SampleMessage("a")

        assertEquals(message.eventId, message.eventId, "get()에서 채번하면 읽을 때마다 다른 키가 나온다")
    }
}
