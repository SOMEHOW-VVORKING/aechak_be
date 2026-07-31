package com.aechak.api.config

import com.aechak.api.support.IntegrationTestBase
import com.aechak.api.user.term.response.TermResponse
import com.aechak.domain.user.term.enums.TermType
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import tools.jackson.databind.ObjectMapper
import java.time.LocalDateTime

/**
 * 실제 컨테이너의 ObjectMapper가 계약 필드명을 그대로 내보내는지 검증.
 * jackson-module-kotlin 부재 시 is-접두 프로퍼티가 "required"처럼 잘려 나가는 회귀를 막는다.
 */
class JacksonWireFormatTest : IntegrationTestBase() {
    @Autowired
    lateinit var objectMapper: ObjectMapper

    @Test
    fun `is-접두 Kotlin 프로퍼티는 필드명이 잘리지 않고 그대로 직렬화된다`() {
        val json =
            objectMapper.writeValueAsString(
                TermResponse(
                    termId = 1L,
                    type = TermType.SERVICE,
                    isRequired = true,
                    version = "1.0",
                    title = "서비스 이용약관",
                    body = "본문",
                    effectiveAt = LocalDateTime.of(2026, 7, 1, 0, 0),
                ),
            )

        assertTrue(json.contains("\"isRequired\":true"), "계약 필드명 보존 실패: $json")
    }
}
