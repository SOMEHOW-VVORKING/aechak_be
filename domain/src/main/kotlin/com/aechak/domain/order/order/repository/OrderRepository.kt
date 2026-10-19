package com.aechak.domain.order.order.repository

import com.aechak.domain.order.order.Order
import com.aechak.domain.order.order.enums.OrderStatus
import java.time.LocalDateTime

interface OrderRepository {
    /** 주문품목은 Order의 cascade로 함께 저장된다. */
    fun saveAll(orders: List<Order>): List<Order>

    /** 배치가 스캔으로 받은 id를 되짚는 단건 조회. */
    fun findById(id: Long): Order?

    fun findAllByOrderGroupId(orderGroupId: Long): List<Order>

    /** 구매자 스코프 단건 조회 — 타인 주문은 없는 것으로 본다. */
    fun findByPublicIdAndBuyerId(
        publicId: String,
        buyerId: Long,
    ): Order?

    /** 셀러 스코프 단건 조회 — 타 셀러 주문은 없는 것으로 본다. */
    fun findByPublicIdAndSellerId(
        publicId: String,
        sellerId: Long,
    ): Order?

    /** 그룹 확정에 따른 셀러 주문 일괄 전이 — 결제대기 행만. 전이된 행 수 반환 */
    fun markAllPaidByOrderGroupId(orderGroupId: Long): Int

    /** 기대 상태인 행만 전이한다 — 동시 전이의 선점 심판. 전이됐으면 true */
    fun transitionStatusIf(
        id: Long,
        expected: OrderStatus,
        next: OrderStatus,
    ): Boolean

    /**
     * 구매확정 기한이 지난 배송완료 주문 id — 진행 중 클레임이 있는 주문은 제외한다.
     * [afterId] 다음 id부터 오름차순 최대 [limit] 건.
     */
    fun findAutoConfirmDueIds(
        dueAtOrBefore: LocalDateTime,
        afterId: Long,
        limit: Int,
    ): List<Long>

    /**
     * 구매확정 사전 안내 대상 주문 id — 기한이 [dueAtAfter] 초과 [dueAtOrBefore] 이하이고 아직 안내하지 않은 배송완료 주문.
     * [afterId] 다음 id부터 오름차순 최대 [limit] 건.
     */
    fun findConfirmNotifyTargetIds(
        dueAtAfter: LocalDateTime,
        dueAtOrBefore: LocalDateTime,
        afterId: Long,
        limit: Int,
    ): List<Long>
}
