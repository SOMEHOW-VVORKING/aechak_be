package com.aechak.api.consumer.review

import com.aechak.application.messaging.ProcessedMessages
import com.aechak.application.review.summary.usecase.ReviewSummaryUseCase
import com.aechak.application.review.summary.usecase.command.ApplyReviewSummaryResultCommand
import com.aechak.infra.kafka.Topics
import com.aechak.infra.kafka.config.MessagingJacksonConfig.Companion.MESSAGING_OBJECT_MAPPER
import com.aechak.message.Envelope
import com.aechak.message.review.ReviewSummaryMessage
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import tools.jackson.databind.ObjectMapper

/** AI 서버가 돌려준 리뷰 요약 결과를 저장함. 멱등 표시와 저장이 한 트랜잭션이라 저장이 실패하면 재배달로 다시 받음 */
@ConditionalOnProperty("spring.kafka.bootstrap-servers")
@Component
class ReviewSummaryResultConsumer(
    @Qualifier(MESSAGING_OBJECT_MAPPER) private val objectMapper: ObjectMapper,
    private val processedMessages: ProcessedMessages,
    private val reviewSummaryUseCase: ReviewSummaryUseCase,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @KafkaListener(topics = [Topics.REVIEW_SUMMARY_RESULT], groupId = GROUP)
    @Transactional
    fun onMessage(value: String) {
        val envelope = objectMapper.readValue(value, Envelope::class.java)
        // 요약 결과만 오는 전용 토픽이라 모르는 종류는 배포가 어긋난 것. 건너뛰면 결과를 영구히 잃어 격리 토픽으로 보냄
        check(envelope.eventType == EVENT_TYPE) { "리뷰 요약 결과 토픽에 모르는 이벤트 종류. eventType=${envelope.eventType} eventId=${envelope.eventId}" }

        val message = objectMapper.readValue(envelope.payload, ReviewSummaryMessage::class.java)
        // 매퍼가 모르는 필드를 조용히 버려서, 버전을 막지 않으면 계약이 바뀌어도 에러 없이 기본값으로 읽힘
        check(message.schemaVersion == ReviewSummaryMessage.SCHEMA_VERSION) {
            "지원하지 않는 리뷰 요약 계약 버전. received=${message.schemaVersion} eventId=${envelope.eventId}"
        }

        if (!processedMessages.markProcessed(GROUP, envelope.eventId)) return

        val applied = reviewSummaryUseCase.applyResult(ApplyReviewSummaryResultCommand(message))
        if (!applied) {
            log.info("최근 요청의 응답이 아니라 추출 결과만 저장함. productId={}, requestId={}", message.productId, message.requestId)
        }
    }

    companion object {
        private const val GROUP = "review-summary-applier"
        private const val EVENT_TYPE = "ReviewSummaryMessage"
    }
}
