package com.aechak.infra.client.ai

import org.springframework.context.annotation.Condition
import org.springframework.context.annotation.ConditionContext
import org.springframework.core.type.AnnotatedTypeMetadata

/**
 * 초안 어댑터 스위치 — 주소와 비밀이 둘 다 있을 때만 조립한다. 비밀 없이 부르면 AI 가 전건
 * 403 이라 주소만 있는 조립은 의미가 없고, 비면 포트가 null 이 되어 파사드가 UNAVAILABLE 을 돌린다.
 *
 * Boot 의 @ConditionalOnProperty 는 autoconfigure 의존이라 Framework 의 Condition 으로 대신한다.
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
