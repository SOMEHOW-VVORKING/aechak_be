package com.aechak.application.order.usecase.result

import com.aechak.domain.order.group.OrderGroup
import java.time.LocalDateTime

/**
 * 만료 스캔 한 건. publicId를 함께 내리는 이유는 호출측이 만료 확정 전에 결제 결과를 조회해야 하고,
 * 결제 식별자가 주문그룹 publicId이기 때문이다.
 */
data class ExpiredOrderGroupResult(
    val orderGroupId: Long,
    val orderGroupPublicId: String,
    val expiresAt: LocalDateTime,
) {
    companion object {
        fun from(orderGroup: OrderGroup): ExpiredOrderGroupResult =
            ExpiredOrderGroupResult(
                orderGroupId = orderGroup.id,
                orderGroupPublicId = orderGroup.publicId,
                expiresAt =
                    requireNotNull(orderGroup.expiresAt) {
                        "만료 스캔 결과에는 만료 시각이 필수입니다 (publicId=${orderGroup.publicId})"
                    },
            )
    }
}
