package com.aechak.application.order.admin.usecase.command

import com.aechak.domain.order.nudge.OrderDispatchNudge

/** 셀러 발송 독촉 기록 입력. 메모 길이 형식 검증은 boot(@Valid)에서 끝난 것이 이 객체의 계약이다. */
data class NudgeOrderDispatchCommand(
    val adminUserId: Long,
    val orderPublicId: String,
    val memo: String?,
) {
    fun toEntity(orderId: Long): OrderDispatchNudge = OrderDispatchNudge.record(orderId = orderId, adminUserId = adminUserId, memo = memo)
}
