package com.aechak.infra.persistence.order.order

import com.aechak.domain.order.order.Order
import com.aechak.domain.order.order.enums.OrderStatus
import com.aechak.domain.order.order.repository.OrderRepository
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository
import java.time.LocalDateTime

interface OrderJpaRepository : JpaRepository<Order, Long> {
    fun findAllByOrderGroupId(orderGroupId: Long): List<Order>

    @Modifying
    @Query(
        "update Order o " +
            "set o.status = :next, o.updatedAt = :now " +
            "where o.orderGroup.id = :groupId and o.status = :expected",
    )
    fun transitionAllStatus(
        @Param("groupId") groupId: Long,
        @Param("expected") expected: OrderStatus,
        @Param("next") next: OrderStatus,
        @Param("now") now: LocalDateTime,
    ): Int
}

@Repository
class OrderRepositoryAdapter(
    private val jpaRepository: OrderJpaRepository,
) : OrderRepository {
    override fun saveAll(orders: List<Order>): List<Order> = jpaRepository.saveAll(orders)

    override fun findAllByOrderGroupId(orderGroupId: Long): List<Order> = jpaRepository.findAllByOrderGroupId(orderGroupId)

    // 벌크 JPQL은 @PreUpdate를 우회하므로 updated_at을 쿼리에서 함께 SET한다
    override fun markAllPaidByOrderGroupId(orderGroupId: Long): Int =
        jpaRepository.transitionAllStatus(
            groupId = orderGroupId,
            expected = OrderStatus.PENDING_PAYMENT,
            next = OrderStatus.PAID,
            now = LocalDateTime.now(),
        )
}
