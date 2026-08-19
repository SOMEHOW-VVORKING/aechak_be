package com.aechak.api.order.group.response

import com.aechak.application.order.usecase.result.CreateOrderGroupResult
import java.time.OffsetDateTime
import java.time.ZoneId

data class CreateOrderGroupResponse(
    val orderGroupId: String,
    /** 절대 시각(오프셋 포함) — FE가 남은 시간을 계산해 타이머를 그린다 */
    val expiresAt: OffsetDateTime,
    val totalProductAmount: Long,
    val totalShippingFee: Long,
    val usedPoint: Long,
    val finalPaymentAmount: Long,
) {
    companion object {
        fun from(result: CreateOrderGroupResult): CreateOrderGroupResponse =
            CreateOrderGroupResponse(
                orderGroupId = result.orderGroupId,
                expiresAt = result.expiresAt.atZone(ZoneId.systemDefault()).toOffsetDateTime(),
                totalProductAmount = result.totalProductAmount,
                totalShippingFee = result.totalShippingFee,
                usedPoint = result.usedPoint,
                finalPaymentAmount = result.finalPaymentAmount,
            )
    }
}
