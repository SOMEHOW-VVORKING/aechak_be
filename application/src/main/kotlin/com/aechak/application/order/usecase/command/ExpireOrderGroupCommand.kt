package com.aechak.application.order.usecase.command

/** 만료 확정 대상. 스캔 결과의 내부 id를 그대로 넘긴다 — 서버 내부 호출이라 식별자를 가리지 않는다 */
data class ExpireOrderGroupCommand(
    val orderGroupId: Long,
)
