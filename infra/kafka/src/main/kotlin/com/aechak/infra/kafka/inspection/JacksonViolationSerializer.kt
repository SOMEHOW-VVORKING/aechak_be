package com.aechak.infra.kafka.inspection

import com.aechak.application.inspection.port.ViolationSerializer
import com.aechak.infra.kafka.config.MessagingJacksonConfig.Companion.MESSAGING_OBJECT_MAPPER
import com.aechak.message.inspection.Violation
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Component
import tools.jackson.databind.ObjectMapper

/**
 * 위반 상세를 JSON 컬럼에 적을 문자열로 바꾼다.
 *
 * 메시지와 같은 매퍼를 쓴다. 다른 매퍼를 쓰면 주고받은 모양과 저장된 모양이 갈라져,
 * 나중에 저장된 값을 계약 타입으로 되읽을 때 조용히 틀어진다.
 */
@Component
class JacksonViolationSerializer(
    @Qualifier(MESSAGING_OBJECT_MAPPER) private val objectMapper: ObjectMapper,
) : ViolationSerializer {
    override fun toJson(violations: List<Violation>): String? =
        if (violations.isEmpty()) null else objectMapper.writeValueAsString(violations)
}
