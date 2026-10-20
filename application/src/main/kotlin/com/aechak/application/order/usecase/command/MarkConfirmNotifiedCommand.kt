package com.aechak.application.order.usecase.command

import java.time.LocalDateTime

/** 구매확정 사전 안내 기록 1건 요청. 회차 전체가 같은 [notifiedAt]을 쓴다. */
data class MarkConfirmNotifiedCommand(
    val orderId: Long,
    val notifiedAt: LocalDateTime,
)
