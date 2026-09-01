package com.aechak.infra.persistence.order.order

import com.aechak.domain.order.order.Order
import com.aechak.domain.order.order.repository.OrderRepository
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository

interface OrderJpaRepository : JpaRepository<Order, Long> {
    fun findAllByOrderGroupIdOrderByIdAsc(orderGroupId: Long): List<Order>

    @Query(
        "select o from Order o left join fetch o._items i " +
            "where o.orderGroup.id = :orderGroupId order by o.id, i.id",
    )
    fun findAllByOrderGroupIdWithItems(
        @Param("orderGroupId") orderGroupId: Long,
    ): List<Order>
}

@Repository
class OrderRepositoryAdapter(
    private val jpaRepository: OrderJpaRepository,
) : OrderRepository {
    override fun saveAll(orders: List<Order>): List<Order> = jpaRepository.saveAll(orders)

    override fun findAllByOrderGroupId(orderGroupId: Long): List<Order> = jpaRepository.findAllByOrderGroupIdOrderByIdAsc(orderGroupId)

    override fun findAllByOrderGroupIdWithItems(orderGroupId: Long): List<Order> =
        jpaRepository.findAllByOrderGroupIdWithItems(orderGroupId)
}
