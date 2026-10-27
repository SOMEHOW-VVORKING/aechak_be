package com.aechak.api.consumer.inspection

import com.aechak.application.inspection.usecase.InspectionUseCase
import com.aechak.application.inspection.usecase.command.ApplyVerdictCommand
import com.aechak.application.messaging.ProcessedMessages
import com.aechak.infra.kafka.Topics
import com.aechak.infra.kafka.config.MessagingJacksonConfig.Companion.MESSAGING_OBJECT_MAPPER
import com.aechak.message.Envelope
import com.aechak.message.inspection.ContentVerdictMessage
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import tools.jackson.databind.ObjectMapper

/**
 * AI 서버가 돌려준 판정을 받아 적는다.
 *
 * 처리 순서가 규약이다. 계약 버전 확인 → 멱등 확인 → 적용 → 처리 기록이 한 트랜잭션에서 돈다.
 * 실패하면 예외를 던져 재시도와 격리 토픽으로 흘려보낸다. 조용히 정상 반환하면 오프셋이
 * 커밋되면서 판정이 영구히 사라지고, 판정은 다시 만들어 낼 방법이 없다.
 *
 * 리스너 동시성을 토픽 파티션 수에 맞춘다. 전역 설정은 1이라서 한 건이 백오프로 막히면
 * 같은 스레드가 모든 파티션을 붙잡는다.
 */
@ConditionalOnProperty("spring.kafka.bootstrap-servers")
@Component
class ContentVerdictConsumer(
    @Qualifier(MESSAGING_OBJECT_MAPPER) private val objectMapper: ObjectMapper,
    private val processedMessages: ProcessedMessages,
    private val inspectionUseCase: InspectionUseCase,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @KafkaListener(topics = [Topics.VERDICT], groupId = GROUP, concurrency = CONCURRENCY)
    @Transactional
    fun onMessage(value: String) {
        val envelope = objectMapper.readValue(value, Envelope::class.java)
        if (envelope.eventType != EVENT_TYPE) {
            log.debug("다루지 않는 이벤트 종류를 건너뛴다. eventType={}", envelope.eventType)
            return
        }

        val verdict = objectMapper.readValue(envelope.payload, ContentVerdictMessage::class.java)
        requireSupportedSchema(verdict, envelope)

        // 멱등 표시와 적용이 같은 트랜잭션에서 커밋된다. 적용이 실패하면 표시도 함께 롤백된다.
        if (!processedMessages.markProcessed(GROUP, envelope.eventId)) {
            log.debug("이미 처리한 판정이다. eventId={}", envelope.eventId)
            return
        }

        inspectionUseCase.applyVerdict(ApplyVerdictCommand(verdict))
    }

    /**
     * 모르는 계약 버전을 거절한다.
     *
     * 메시지 매퍼가 모르는 필드를 조용히 버리도록 설정돼 있어서, 버전을 명시적으로 막지 않으면
     * 저쪽이 필드를 바꿨을 때 에러 없이 기본값으로 파싱된다. 그런 드리프트는 운영 중에
     * 판정이 이상해진 뒤에야 발견된다.
     */
    private fun requireSupportedSchema(
        verdict: ContentVerdictMessage,
        envelope: Envelope,
    ) {
        if (verdict.schemaVersion == ContentVerdictMessage.SCHEMA_VERSION) return
        throw IllegalStateException(
            "지원하지 않는 판정 계약 버전이다. " +
                "received=${verdict.schemaVersion} supported=${ContentVerdictMessage.SCHEMA_VERSION} " +
                "eventId=${envelope.eventId}",
        )
    }

    companion object {
        private const val GROUP = "content-verdict-applier"
        private const val EVENT_TYPE = "ContentVerdictMessage"

        /** 판정 토픽 파티션 수와 같게 둔다. 설정과 어긋나면 남는 파티션이 소비되지 않는다 */
        private const val CONCURRENCY = "3"
    }
}
