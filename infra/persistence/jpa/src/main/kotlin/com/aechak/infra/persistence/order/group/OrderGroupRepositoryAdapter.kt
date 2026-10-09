package com.aechak.infra.persistence.order.group

import com.aechak.domain.order.group.OrderGroup
import com.aechak.domain.order.group.repository.OrderGroupRepository
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

interface OrderGroupJpaRepository : JpaRepository<OrderGroup, Long> {
    fun findByIdempotencyKey(idempotencyKey: String): OrderGroup?

    fun findByPublicId(publicId: String): OrderGroup?
}

@Repository
class OrderGroupRepositoryAdapter(
    private val jpaRepository: OrderGroupJpaRepository,
) : OrderGroupRepository {
    override fun save(orderGroup: OrderGroup): OrderGroup = jpaRepository.save(orderGroup)

    override fun findByIdempotencyKey(idempotencyKey: String): OrderGroup? = jpaRepository.findByIdempotencyKey(idempotencyKey)

    override fun findByPublicId(publicId: String): OrderGroup? = jpaRepository.findByPublicId(publicId)
}
