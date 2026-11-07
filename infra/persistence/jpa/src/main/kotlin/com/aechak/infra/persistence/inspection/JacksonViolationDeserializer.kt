package com.aechak.infra.persistence.inspection

import com.aechak.application.inspection.port.ViolationDeserializer
import com.aechak.application.inspection.port.view.ViolationView
import org.springframework.stereotype.Component
import tools.jackson.core.JacksonException
import tools.jackson.databind.DeserializationFeature
import tools.jackson.databind.ObjectMapper
import tools.jackson.databind.json.JsonMapper
import tools.jackson.module.kotlin.kotlinModule
import tools.jackson.module.kotlin.readValue

@Component
class JacksonViolationDeserializer : ViolationDeserializer {
    // 저장 쪽 MessagingJacksonConfig와 설정을 같게 둠. 갈라지면 저장된 값을 조용히 다르게 읽음
    private val objectMapper: ObjectMapper =
        JsonMapper
            .builder()
            .addModule(kotlinModule())
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .build()

    override fun fromJson(json: String): List<ViolationView>? =
        try {
            objectMapper
                .readValue<List<ViolationView?>>(json)
                .takeIf { null !in it } // 배열 원소 자리의 null은 kotlinModule이 거르지 않음
                ?.filterNotNull()
        } catch (e: JacksonException) {
            null
        }
}
