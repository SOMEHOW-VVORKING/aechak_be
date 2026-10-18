package com.aechak.infra.persistence.order.order

import com.aechak.domain.order.order.OrderStatusHistory
import com.aechak.domain.order.order.repository.OrderStatusHistoryRepository
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

interface OrderStatusHistoryJpaRepository : JpaRepository<OrderStatusHistory, Long>

@Repository
class OrderStatusHistoryRepositoryAdapter(
    private val jpaRepository: OrderStatusHistoryJpaRepository,
) : OrderStatusHistoryRepository {
    override fun save(history: OrderStatusHistory): OrderStatusHistory = jpaRepository.save(history)
}
