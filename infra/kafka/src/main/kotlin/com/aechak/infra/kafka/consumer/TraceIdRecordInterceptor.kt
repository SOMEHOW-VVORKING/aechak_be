package com.aechak.infra.kafka.consumer

import org.apache.kafka.clients.consumer.Consumer
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.slf4j.MDC
import org.springframework.kafka.listener.RecordInterceptor
import org.springframework.stereotype.Component
import java.util.UUID

/**
 * 릴레이(OutboxRelay.send)가 발행 시점의 traceId를 레코드 헤더에 실어 보내는데, 리스너 스레드의 MDC는 비어 있어서 복원이 필요함.
 * 컨슈머가 후속 이벤트를 발행할 때 traceId를 통해 이벤트 체이닝의 로깅을 하기 위함.
 *
 * 헤더가 없는 레코드(외부 시스템 발행 등)는 새 traceId를 만들어 줌.
 */
@Component
class TraceIdRecordInterceptor : RecordInterceptor<Any, Any> {
    override fun intercept(
        record: ConsumerRecord<Any, Any>,
        consumer: Consumer<Any, Any>,
    ): ConsumerRecord<Any, Any> {
        val fromHeader =
            record
                .headers()
                .lastHeader(TRACE_ID_HEADER)
                ?.value()
                ?.toString(Charsets.UTF_8)
        MDC.put(TRACE_ID_MDC_KEY, if (fromHeader.isNullOrBlank()) UUID.randomUUID().toString() else fromHeader)
        return record
    }

    override fun afterRecord(
        record: ConsumerRecord<Any, Any>,
        consumer: Consumer<Any, Any>,
    ) {
        MDC.remove(TRACE_ID_MDC_KEY)
    }

    companion object {
        /** 릴레이가 사용하는 레코드 헤더 이름 */
        const val TRACE_ID_HEADER = "trace_id"

        /**
         * MDC 키. TraceIdFilter·logback %X{traceId}와 값이 같아야 함.
         */
        const val TRACE_ID_MDC_KEY = "traceId"
    }
}
