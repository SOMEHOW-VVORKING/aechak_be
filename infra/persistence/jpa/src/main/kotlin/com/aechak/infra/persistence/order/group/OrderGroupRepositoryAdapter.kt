package com.aechak.infra.persistence.order.group

import com.aechak.domain.order.group.OrderGroup
import com.aechak.domain.order.group.enums.OrderGroupStatus
import com.aechak.domain.order.group.repository.OrderGroupRepository
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import java.time.LocalDateTime

interface OrderGroupJpaRepository : JpaRepository<OrderGroup, Long> {
    fun findByIdempotencyKey(idempotencyKey: String): OrderGroup?

    fun countByBuyerIdAndStatusAndExpiresAtGreaterThanEqual(
        buyerId: Long,
        status: OrderGroupStatus,
        now: LocalDateTime,
    ): Long
}

@Repository
class OrderGroupRepositoryAdapter(
    private val jpaRepository: OrderGroupJpaRepository,
) : OrderGroupRepository {
    override fun save(orderGroup: OrderGroup): OrderGroup = jpaRepository.save(orderGroup)

    override fun findByIdempotencyKey(idempotencyKey: String): OrderGroup? = jpaRepository.findByIdempotencyKey(idempotencyKey)

    override fun countUnexpiredPendingPayment(
        buyerId: Long,
        now: LocalDateTime,
    ): Long = jpaRepository.countByBuyerIdAndStatusAndExpiresAtGreaterThanEqual(buyerId, OrderGroupStatus.PENDING_PAYMENT, now)
}
