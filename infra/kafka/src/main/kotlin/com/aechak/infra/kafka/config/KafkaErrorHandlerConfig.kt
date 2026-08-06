package com.aechak.infra.kafka.config

import com.aechak.infra.kafka.Topics
import org.apache.kafka.common.TopicPartition
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer
import org.springframework.kafka.listener.DefaultErrorHandler
import org.springframework.kafka.support.ExponentialBackOffWithMaxRetries

/**
 * 3회 짧게 재시도 후 DLT로 보냄
 * 재시도 시에는 파티션이 블로킹됨
 */
@Configuration
class KafkaErrorHandlerConfig {
    @Bean
    fun defaultErrorHandler(template: KafkaTemplate<String, String>): DefaultErrorHandler {
        val recoverer =
            DeadLetterPublishingRecoverer(template) { record, _ ->
                TopicPartition(Topics.dltOf(record.topic()), -1)
            }
        val backoff =
            ExponentialBackOffWithMaxRetries(3).apply {
                initialInterval = 1_000
                multiplier = 2.0
                maxInterval = 10_000
            }
        return DefaultErrorHandler(recoverer, backoff)
    }
}
