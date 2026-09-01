package com.aechak.domain.order.order.repository

import com.aechak.domain.order.order.Order

interface OrderRepository {
    /** 주문품목은 Order의 cascade로 함께 저장된다. */
    fun saveAll(orders: List<Order>): List<Order>

    /** 주문 id 오름차순 */
    fun findAllByOrderGroupId(orderGroupId: Long): List<Order>

    /** 주문 id 오름차순. 품목도 id 오름차순 */
    fun findAllByOrderGroupIdWithItems(orderGroupId: Long): List<Order>
}
