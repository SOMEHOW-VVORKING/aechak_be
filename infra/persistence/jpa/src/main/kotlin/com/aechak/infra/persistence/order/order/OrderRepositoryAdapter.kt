package com.aechak.infra.persistence.order.order

import com.aechak.domain.order.order.Order
import com.aechak.domain.order.order.repository.OrderRepository
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

interface OrderJpaRepository : JpaRepository<Order, Long>

@Repository
class OrderRepositoryAdapter(
    private val jpaRepository: OrderJpaRepository,
) : OrderRepository {
    override fun saveAll(orders: List<Order>): List<Order> = jpaRepository.saveAll(orders)
}
