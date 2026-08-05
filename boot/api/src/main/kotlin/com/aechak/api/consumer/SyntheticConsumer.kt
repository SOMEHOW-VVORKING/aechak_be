package com.aechak.api.consumer

import com.aechak.application.messaging.ProcessedMessages
import com.aechak.infra.kafka.Topics
import com.aechak.infra.kafka.config.MessagingJacksonConfig.Companion.MESSAGING_OBJECT_MAPPER
import com.aechak.message.Envelope
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.stereotype.Component
import tools.jackson.databind.ObjectMapper

/**
 * 테스트용 메시지를 소비하는 임시 컨슈머. 예시임.
 * TODO: 첫 실제 도메인 컨슈머가 생기면 이 클래스를 삭제한다 (소비 패턴은 그 컨슈머가 이어받는다).
 * 단 트랜잭션은 이어받을 것이 없음. 실제 컨슈머는 markProcessed와 처리를 한 트랜잭션으로 묶을 것.
 * 안 묶으면 mark만 커밋된 뒤 처리가 실패해 조용히 유실됨.
 *
 * 브로커 주소가 설정된 컨텍스트에서만 뜬다. Kafka를 안 쓰는 통합 테스트 컨텍스트에서까지
 * 리스너가 기본값 localhost:9092로 접속을 재시도하며 경고를 쌓는 것을 막는다.
 */
@ConditionalOnProperty("spring.kafka.bootstrap-servers")
@Component
class SyntheticConsumer(
    @Qualifier(MESSAGING_OBJECT_MAPPER) private val objectMapper: ObjectMapper,
    private val processedMessages: ProcessedMessages,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @KafkaListener(
        topics = [Topics.ORDER],
        groupId = GROUP,
    )
    fun onMessage(value: String) {
        val envelope = objectMapper.readValue(value, Envelope::class.java)

        // 한 토픽에는 여러 타입의 메시지가 흐른다. 내 담당이 아니면 그냥 넘어간다.
        if (envelope.eventType != "SyntheticMessage") return

        // 같은 메시지는 두 번 배달될 수 있다. 이미 처리한 것이면 조용히 넘어간다.
        // 여기서 예외를 던지면 안 된다 — 중복은 오류가 아니라 "이미 끝난 일"이고,
        // 예외를 던지는 순간 멀쩡한 중복 메시지가 재시도를 거쳐 DLT까지 흘러간다.
        if (!processedMessages.markProcessed(GROUP, envelope.eventId)) {
            log.info("중복 스킵: eventId={}", envelope.eventId)
            return
        }

        // 실제 컨슈머라면 여기서 payload를 메시지 클래스로 변환해 UseCase를 호출.
        // 이 클래스는 파이프 확인이 목적이라 로그만 남긴다.
        log.info("합성 메시지 처리: eventId={}, payload={}", envelope.eventId, envelope.payload)
    }

    companion object {
        // Kafka 그룹 이름이자, 인박스(processed_message)에 기록하는 컨슈머 이름.
        // 두 곳의 값이 반드시 같아야 해서 상수 하나로 묶음.
        private const val GROUP = "synthetic-skeleton"
    }
}
