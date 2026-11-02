package com.aechak.api.config

import com.aechak.infra.kafka.Topics
import org.apache.kafka.clients.admin.NewTopic
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.kafka.config.TopicBuilder

/** 리뷰 요약 토픽. AI 서버는 처리할 수 없는 요청이 오면 그 파티션을 멈추므로, 요청 토픽을 나눠 한 상품이 전체를 세우지 않게 함 */
@ConditionalOnProperty("spring.kafka.bootstrap-servers")
@Configuration
class ReviewSummaryTopicConfig {
    @Bean
    fun reviewSummaryRequestTopic(): NewTopic =
        TopicBuilder
            .name(Topics.REVIEW_SUMMARY_REQUEST)
            .partitions(REQUEST_PARTITIONS)
            .replicas(REPLICAS)
            .build()

    @Bean
    fun reviewSummaryResultTopic(): NewTopic =
        TopicBuilder
            .name(Topics.REVIEW_SUMMARY_RESULT)
            .partitions(1)
            .replicas(REPLICAS)
            .build()

    @Bean
    fun reviewSummaryResultDltTopic(): NewTopic =
        TopicBuilder
            .name(Topics.REVIEW_SUMMARY_RESULT_DLT)
            .partitions(1)
            .replicas(REPLICAS)
            .build()

    companion object {
        private const val REQUEST_PARTITIONS = 3
        private const val REPLICAS = 1
    }
}
