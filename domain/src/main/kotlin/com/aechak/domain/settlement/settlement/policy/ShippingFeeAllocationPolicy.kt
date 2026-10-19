package com.aechak.domain.settlement.settlement.policy

import com.aechak.common.error.BusinessException
import com.aechak.domain.settlement.error.SettlementErrorCode

/**
 * 주문 하나의 셀러 배송비를 그 주문의 품목 행에 안분한다.
 * 원장은 주문품목 단위라 배송비도 품목별로 쪼개 들어가고, 쪼갠 값의 합은 원래 배송비와 정확히 같아야 한다.
 */
object ShippingFeeAllocationPolicy {
    /**
     * 품목 수로 균등 분배하고 1원 단위 나머지는 주문품목 id가 작은 쪽부터 한 원씩 얹는다.
     * 같은 입력이면 항상 같은 배분이라 재실행해도 품목별 금액이 흔들리지 않는다.
     */
    fun allocate(
        totalShippingFee: Long,
        orderItemIds: List<Long>,
    ): Map<Long, Long> {
        if (totalShippingFee < 0 || orderItemIds.isEmpty() || orderItemIds.distinct().size != orderItemIds.size) {
            throw BusinessException(SettlementErrorCode.INVALID_SETTLEMENT_AMOUNT)
        }
        val ids = orderItemIds.sorted()
        val share = totalShippingFee / ids.size
        val remainder = totalShippingFee % ids.size
        return ids.withIndex().associate { (index, id) -> id to share + if (index < remainder) 1L else 0L }
    }
}
