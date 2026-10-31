package com.aechak.infra.client.ai

import org.springframework.context.annotation.Condition
import org.springframework.context.annotation.ConditionContext
import org.springframework.core.type.AnnotatedTypeMetadata

/**
 * 초안 어댑터 스위치 — **주소와 비밀이 둘 다 있을 때만 조립한다.**
 *
 * 비밀 없이 호출하면 AI가 전건 403으로 거부하므로(fail closed) 주소만 있는 조립은 의미가 없다.
 * 둘 중 하나가 비면 포트가 null이 되어 파사드가 UNAVAILABLE을 돌리고, local·test에서 아무것도
 * 적지 않아도 부팅이 된다.
 *
 * Boot의 @ConditionalOnProperty는 autoconfigure 모듈 의존이라 이 모듈(spring-context만)에서는
 * Framework의 Condition으로 같은 일을 한다(MockShipmentTrackingEnabled 선례).
 */
class AiDraftClientEnabled : Condition {
    override fun matches(
        context: ConditionContext,
        metadata: AnnotatedTypeMetadata,
    ): Boolean =
        context.environment.getProperty("$PREFIX.base-url", "").isNotBlank() &&
            context.environment.getProperty("$PREFIX.shared-secret", "").isNotBlank()

    companion object {
        const val PREFIX = "aechak.ai.draft"
    }
}
