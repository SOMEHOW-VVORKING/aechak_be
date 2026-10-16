package com.aechak.infra.client.payment.reconciliation

import org.springframework.context.annotation.Condition
import org.springframework.context.annotation.ConditionContext
import org.springframework.core.type.AnnotatedTypeMetadata

/**
 * 설정값으로 대사 소스를 켜고 끄는 조건. Boot의 @ConditionalOnProperty는 autoconfigure 모듈 의존이라
 * 이 모듈(spring-context만)에서는 Framework의 Condition으로 같은 일을 한다.
 */
abstract class PropertyEnabledCondition(
    private val property: String,
    private val defaultValue: Boolean,
) : Condition {
    override fun matches(
        context: ConditionContext,
        metadata: AnnotatedTypeMetadata,
    ): Boolean = context.environment.getProperty(property, Boolean::class.java, defaultValue)
}

/** 포트원 대사 소스 — 기본 켜짐. 테스트처럼 실 포트원으로 나가면 안 되는 컨텍스트가 끈다 */
class PortOneReconciliationEnabled : PropertyEnabledCondition("payment.reconciliation.portone.enabled", true)

/** 토스 대사 소스 — 기본 꺼짐. 토스 직접 연동(orderId == 우리 결제 식별자)과 조회 권한을 확인한 환경만 켠다 */
class TossReconciliationEnabled : PropertyEnabledCondition("payment.toss.enabled", false)
