package com.aechak.application.order.delivery.usecase

import com.aechak.application.order.delivery.usecase.command.CompleteDeliveryBySystemCommand
import com.aechak.application.order.delivery.usecase.result.PendingDeliveryResult
import java.time.LocalDateTime

/**
 * 배치가 쓰는 배송 진입점 계약. 셀러센터가 쓰는 SellerOrderUseCase와 나눠 둔다 —
 * 배치는 셀러 자격 검증을 끌고 오지 않는다.
 */
interface OrderDeliveryUseCase {
    /** 발송 후 기준 시각이 지났는데 아직 배송완료가 아닌 배송중 주문. */
    fun findPendingDeliveries(
        dispatchedBefore: LocalDateTime,
        limit: Int,
    ): List<PendingDeliveryResult>

    /** 시간 경과에 따른 자동 배송완료 전이. */
    fun completeDeliveryBySystem(command: CompleteDeliveryBySystemCommand)
}
