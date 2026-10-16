package com.aechak.domain.order.order.repository

import com.aechak.domain.order.order.Order

interface OrderRepository {
    /** 주문품목은 Order의 cascade로 함께 저장된다. */
    fun saveAll(orders: List<Order>): List<Order>

    fun findAllByOrderGroupId(orderGroupId: Long): List<Order>

    /** 그룹 확정에 따른 셀러 주문 일괄 전이 — 결제대기 행만. 전이된 행 수 반환 */
    fun markAllPaidByOrderGroupId(orderGroupId: Long): Int
}
