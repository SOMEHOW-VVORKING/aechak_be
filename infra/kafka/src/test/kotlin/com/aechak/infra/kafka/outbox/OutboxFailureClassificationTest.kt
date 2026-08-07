package com.aechak.infra.kafka.outbox

import org.apache.kafka.common.errors.RecordTooLargeException
import org.apache.kafka.common.errors.TimeoutException
import java.util.concurrent.ExecutionException
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** [단위] 영구 실패 판정. 이 목록이 넓어지면 잠깐 죽은 브로커 때문에 멀쩡한 행이 DEAD로 죽는다 */
class OutboxFailureClassificationTest {
    @Test
    fun `크기 초과는 다시 보내도 결과가 같다`() {
        assertTrue(isPermanentFailure(RecordTooLargeException("too big")), "크기 초과 행을 계속 재시도하면 그 행이 큐 선두를 영원히 막는다")
    }

    @Test
    fun `get이 감싼 예외도 원인을 훑어 판정한다`() {
        assertTrue(
            isPermanentFailure(ExecutionException(RecordTooLargeException("too big"))),
            "get()이 감싼 예외를 못 벗기면 판정이 통째로 무력화된다",
        )
    }

    @Test
    fun `타임아웃은 영구 실패가 아니다`() {
        assertFalse(isPermanentFailure(TimeoutException("broker slow")), "잠깐 죽은 브로커 때문에 멀쩡한 행이 DEAD로 종결된다")
        assertFalse(
            isPermanentFailure(ExecutionException(TimeoutException("broker slow"))),
            "감싼 경우도 마찬가지다",
        )
    }
}
