package com.aechak.api.config

import com.aechak.infra.kafka.Topics
import org.apache.kafka.clients.admin.NewTopic
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.kafka.config.TopicBuilder

/**
 * 검수 토픽을 파티션 수를 명시해 만든다.
 *
 * 브로커가 자동 생성에 맡기면 기본값 1개로 생기고, 재시도가 파티션을 블로킹하는 구조와 겹쳐
 * 처리 불가 한 건이 판정 스트림 전체를 멈춘다. 그래서 처음에 정해 두어야 한다.
 * 나중에 늘리면 orderingKey 해싱이 재배치돼 같은 상품의 순서 보장이 깨진다.
 *
 * 브로커가 replication factor 1인 단일 노드라 복제본을 1로 둔다.
 */
@ConditionalOnProperty("spring.kafka.bootstrap-servers")
@Configuration
class InspectionTopicConfig {
    @Bean
    fun inspectionTopic(): NewTopic =
        TopicBuilder
            .name(Topics.INSPECTION)
            .partitions(PARTITIONS)
            .replicas(REPLICAS)
            .build()

    @Bean
    fun inspectionDltTopic(): NewTopic =
        TopicBuilder
            .name(Topics.INSPECTION_DLT)
            .partitions(1)
            .replicas(REPLICAS)
            .build()

    @Bean
    fun verdictTopic(): NewTopic =
        TopicBuilder
            .name(Topics.VERDICT)
            .partitions(PARTITIONS)
            .replicas(REPLICAS)
            .build()

    @Bean
    fun verdictDltTopic(): NewTopic =
        TopicBuilder
            .name(Topics.VERDICT_DLT)
            .partitions(1)
            .replicas(REPLICAS)
            .build()

    companion object {
        /**
         * 판정 한 건이 막혀도 나머지가 흐르도록 셋으로 둔다.
         * 소비 쪽은 리스너에 concurrency를 따로 걸어야 실제로 병렬이 된다.
         */
        private const val PARTITIONS = 3

        /** DLT는 순서도 병렬성도 필요 없다 */
        private const val REPLICAS = 1
    }
}
