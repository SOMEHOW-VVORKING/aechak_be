package com.aechak.application.order.usecase.result

import com.aechak.domain.order.group.OrderGroup
import java.time.LocalDateTime

/** 내부 id는 노출하지 않는다 — orderGroupId는 publicId(ULID)이고 이후 결제 준비의 paymentId로 재사용된다. */
data class CreateOrderGroupResult(
    val orderGroupId: String,
    val expiresAt: LocalDateTime,
    val totalProductAmount: Long,
    val totalShippingFee: Long,
    val usedPoint: Long,
    val finalPaymentAmount: Long,
) {
    companion object {
        fun from(orderGroup: OrderGroup): CreateOrderGroupResult =
            CreateOrderGroupResult(
                orderGroupId = orderGroup.publicId,
                expiresAt = requireNotNull(orderGroup.expiresAt) { "주문그룹 생성 결과에는 만료 시각이 필수입니다 (publicId=${orderGroup.publicId})" },
                totalProductAmount = orderGroup.totalProductAmount,
                totalShippingFee = orderGroup.totalShippingFee,
                usedPoint = orderGroup.usedPoint,
                finalPaymentAmount = orderGroup.finalPaymentAmount,
            )
    }
}
