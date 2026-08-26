package com.aechak.application.user.point.usecase.command

/** 쓰기 도메인(주문 등)이 넘기는 적립금 사용 명세 — 결정적 멱등키·출처 표기는 호출 도메인의 규칙을 따른다. */
data class UsePointCommand(
    val userId: Long,
    val amount: Long,
    val idempotencyKey: String,
    val sourceType: String? = null,
    val sourceId: Long? = null,
)
