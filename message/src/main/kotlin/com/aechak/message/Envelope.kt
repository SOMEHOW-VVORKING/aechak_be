package com.aechak.message

import java.time.Instant

/**
 * 메세지 담는 형태.
 */
data class Envelope(
    val eventId: String,
    val eventType: String,
    val eventVersion: Int, // 스키마 버전
    val occurredAt: Instant,
    val aggregateType: String,
    val aggregateId: String,
    val traceId: String,
    val producer: String,
    val payload: String, // Message 본문 JSON 문자열
)
