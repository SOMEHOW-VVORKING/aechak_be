package com.aechak.infra.kafka.config

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import tools.jackson.databind.DeserializationFeature
import tools.jackson.databind.ObjectMapper
import tools.jackson.databind.json.JsonMapper
import tools.jackson.module.kotlin.kotlinModule

/**
 * 메시징 전용 ObjectMapper. Boot의 웹 매퍼와 분리함.
 */
@Configuration
class MessagingJacksonConfig {
    @Bean(name = [MESSAGING_OBJECT_MAPPER])
    fun messagingObjectMapper(): ObjectMapper =
        JsonMapper
            .builder()
            .addModule(kotlinModule())
            // 역직렬화 시 모르는 필드는 스킵. default 설정이지만, 명시.
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .build()

    companion object {
        const val MESSAGING_OBJECT_MAPPER = "messagingObjectMapper"
    }
}
