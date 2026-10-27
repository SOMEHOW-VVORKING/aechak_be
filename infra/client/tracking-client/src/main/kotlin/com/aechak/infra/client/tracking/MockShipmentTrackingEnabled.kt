package com.aechak.infra.client.tracking

import org.springframework.context.annotation.Condition
import org.springframework.context.annotation.ConditionContext
import org.springframework.core.type.AnnotatedTypeMetadata

/**
 * 모의 추적 어댑터 스위치 — **기본 꺼짐**. 적지 않으면 어댑터가 조립되지 않는다.
 * Boot의 @ConditionalOnProperty는 autoconfigure 모듈 의존이라 이 모듈(spring-context만)에서는
 * Framework의 Condition으로 같은 일을 한다.
 */
class MockShipmentTrackingEnabled : Condition {
    override fun matches(
        context: ConditionContext,
        metadata: AnnotatedTypeMetadata,
    ): Boolean = context.environment.getProperty(PROPERTY, Boolean::class.java, false)

    companion object {
        const val PROPERTY = "order.delivery.tracking.mock.enabled"
    }
}
