package com.aechak.message

import java.time.Instant
import kotlin.time.Duration

sealed interface IntegrationMessage {
    /**
     * 같은 사건인지 가려내는 번호. 멱등성 보장을 위함.
     * 생성 시 한 번 정하고 고정. 읽을 때마다 채번하면 같은 사건이 다른 사건이 됨.
     */
    val eventId: String

    /** 사건 발생 시각. 발행 시각이 아니라 재발행해도 고정 */
    val occurredAt: Instant

    /** 발행 도메인 이름('order' 등 소문자). 토픽 이름이 여기서 조립됨 */
    val aggregateType: String

    /** 파티션 키(주문ID 등). 같은 키는 같은 파티션으로 감. 발행 순서는 보장 안 함 */
    val aggregateId: String
}

/** 유실 불가. 아웃박스에 기록해 발행될 때까지 재시도함 */
interface GuaranteedMessage : IntegrationMessage {
    /** 발행 기한. 넘기면 발행을 멈추고 사람이 재개할 때까지 붙잡음 */
    val allowedDelay: Duration
        get() = Duration.INFINITE
}

/** 유실 가능. 발행 실패 시 버려지고 재시도 없음 */
interface BestEffortMessage : IntegrationMessage
