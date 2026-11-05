package com.aechak.api.consumer.deadletter

import com.aechak.infra.kafka.Topics
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.kafka.support.KafkaHeaders
import org.springframework.stereotype.Component
import java.security.MessageDigest

/**
 * 재시도를 소진해 DLT로 격리된 메시지를 로그로 남긴다.
 *
 * 이 리스너가 없으면 백오프 3회를 넘긴 메시지가 DLT에 쌓인 뒤 아무도 보지 않는다.
 * 특히 판정 결과는 다시 만들어 낼 방법이 없어 조용히 사라진다.
 *
 * 영속 테이블을 두지 않은 이유는 플라이웨이 번호가 열린 PR들과 다투는 것을 피하고,
 * 검수 보정 스케줄이 미판정 건을 다시 요청하므로 재생이 아니라 가시성만 필요하기 때문이다.
 *
 * **본문을 로그에 남기지 않는다.** 상품 설명은 마스킹 대상이 아니고, 판정 결과의 인용은
 * 모델이 원문에서 그대로 떠온 발췌문이다. 분량을 자르는 것은 용량 제한이지 내용 차단이
 * 아니므로, 길이와 지문만 남기고 원문은 남기지 않는다. 오류 레벨 로그는 수집·알림 경로를
 * 타서 접근 범위가 넓다.
 *
 * 이 핸들러는 어떤 경우에도 예외를 던지지 않는다. 던지면 DLT의 DLT를 만들려고 한다.
 * 그래서 Error 계열까지 함께 막는다.
 */
@ConditionalOnProperty("spring.kafka.bootstrap-servers")
@Component
class DeadLetterRecorder {
    private val log = LoggerFactory.getLogger(javaClass)

    @KafkaListener(
        topics = [Topics.ORDER_DLT, Topics.REVIEW_DLT, Topics.INSPECTION_DLT, Topics.VERDICT_DLT, Topics.REVIEW_SUMMARY_RESULT_DLT],
        groupId = GROUP,
    )
    fun onDeadLetter(record: ConsumerRecord<String, String>) {
        try {
            log.error(
                "DLT 격리 메시지. topic={}, partition={}, offset={}, key={}, originalTopic={}, " +
                    "exceptionType={}, exceptionMessage={}, valueLength={}, valueDigest={}",
                record.topic(),
                record.partition(),
                record.offset(),
                record.key(),
                header(record, KafkaHeaders.DLT_ORIGINAL_TOPIC),
                header(record, KafkaHeaders.DLT_EXCEPTION_FQCN),
                header(record, KafkaHeaders.DLT_EXCEPTION_MESSAGE),
                record.value()?.length ?: 0,
                digestOf(record.value()),
            )
        } catch (e: Throwable) {
            // 로깅 자체가 실패해도 이 메서드를 빠져나가지 않게 한다.
            runCatching {
                log.error("DLT 기록 중 실패. topic={}, offset={}", record.topic(), record.offset(), e)
            }
        }
    }

    private fun header(
        record: ConsumerRecord<String, String>,
        name: String,
    ): String =
        record
            .headers()
            .lastHeader(name)
            ?.value()
            ?.decodeToString() ?: "-"

    /**
     * 본문 대신 지문을 남긴다.
     *
     * 같은 메시지가 반복 격리되는지 확인하려면 식별자가 필요하지만, 그 식별자가 내용을
     * 드러내서는 안 된다. 앞 16자리만 쓰는 것은 로그 가독성 때문이고, 충돌을 가려내는 데는
     * 토픽·오프셋이 함께 있어 충분하다.
     */
    private fun digestOf(value: String?): String {
        if (value == null) return "-"
        val bytes = MessageDigest.getInstance("SHA-256").digest(value.toByteArray())
        return bytes.joinToString("", limit = DIGEST_BYTES, truncated = "") { "%02x".format(it) }
    }

    companion object {
        private const val GROUP = "dead-letter-recorder"

        /** 지문 앞 8바이트(16자리)만 남긴다 */
        private const val DIGEST_BYTES = 8
    }
}
