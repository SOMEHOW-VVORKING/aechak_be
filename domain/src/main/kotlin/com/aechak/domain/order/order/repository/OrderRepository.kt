package com.aechak.domain.order.order.repository

import com.aechak.domain.order.order.Order

interface OrderRepository {
    /** 주문품목은 Order의 cascade로 함께 저장된다. */
    fun saveAll(orders: List<Order>): List<Order>
}
