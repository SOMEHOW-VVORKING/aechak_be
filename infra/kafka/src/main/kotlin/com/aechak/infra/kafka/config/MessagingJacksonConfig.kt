package com.aechak.infra.kafka.config

import com.aechak.message.GuaranteedMessage
import com.fasterxml.jackson.annotation.JsonIgnore
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import tools.jackson.databind.DeserializationFeature
import tools.jackson.databind.ObjectMapper
import tools.jackson.databind.json.JsonMapper
import tools.jackson.module.kotlin.kotlinModule
import kotlin.time.Duration

@Configuration
class MessagingJacksonConfig {
    @Bean(name = [MESSAGING_OBJECT_MAPPER])
    fun messagingObjectMapper(): ObjectMapper =
        JsonMapper
            .builder()
            .addModule(kotlinModule())
            // 역직렬화 시 모르는 필드는 스킵. default 설정이지만, 명시.
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            // allowedDelay는 발행 쪽 설정이라 밖으로 안 나감
            .addMixIn(GuaranteedMessage::class.java, GuaranteedMessageMixIn::class.java)
            .build()

    private interface GuaranteedMessageMixIn {
        @get:JsonIgnore
        val allowedDelay: Duration
    }

    companion object {
        const val MESSAGING_OBJECT_MAPPER = "messagingObjectMapper"
    }
}
