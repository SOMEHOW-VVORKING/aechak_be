package com.aechak.api.eventbackbone

import com.aechak.infra.kafka.consumer.TraceIdRecordInterceptor
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.apache.kafka.clients.consumer.MockConsumer
import org.apache.kafka.clients.consumer.OffsetResetStrategy
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.slf4j.MDC

/** [단위] traceId 복원 인터셉터 — 헤더 복원·폴백 생성·스레드 정리 계약 검증. */
class TraceIdRecordInterceptorTest {
    private val interceptor = TraceIdRecordInterceptor()
    private val consumer = MockConsumer<Any, Any>(OffsetResetStrategy.EARLIEST)

    @AfterEach
    fun clearMdc() = MDC.clear()

    @Test
    fun `헤더의 traceId를 MDC로 복원한다`() {
        val record = record()
        record.headers().add(TraceIdRecordInterceptor.TRACE_ID_HEADER, "trace-abc".toByteArray())

        interceptor.intercept(record, consumer)

        // 키를 리터럴로 단언하는 이유: TraceIdFilter·logback 패턴(%X{traceId})과 "값"의 정합을 고정한다.
        // 이 테스트가 깨지면 그 두 곳도 함께 바꿔야 한다는 뜻이다.
        assertThat(MDC.get("traceId"))
            .`as`("발행 홉이 실어 보낸 traceId가 리스너 스레드 MDC로 이어져야 한다")
            .isEqualTo("trace-abc")
    }

    @Test
    fun `헤더가 없으면 새 traceId를 만들어 체인의 뿌리로 삼는다`() {
        interceptor.intercept(record(), consumer)

        assertThat(MDC.get("traceId"))
            .`as`("헤더 없는 레코드도 이 지점부터의 체인은 묶여야 한다")
            .isNotBlank()
    }

    @Test
    fun `처리가 끝나면 MDC를 지운다`() {
        interceptor.intercept(record(), consumer)
        interceptor.afterRecord(record(), consumer)

        assertThat(MDC.get("traceId"))
            .`as`("리스너 스레드는 풀에서 재사용되므로 이전 레코드의 traceId가 남으면 안 된다")
            .isNull()
    }

    private fun record(): ConsumerRecord<Any, Any> = ConsumerRecord("topic", 0, 0, "key", "value")
}
