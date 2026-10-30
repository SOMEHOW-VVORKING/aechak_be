package com.aechak.infra.persistence.order.nudge

import com.aechak.domain.order.nudge.OrderDispatchNudge
import com.aechak.domain.order.nudge.repository.OrderDispatchNudgeRepository
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

interface OrderDispatchNudgeJpaRepository : JpaRepository<OrderDispatchNudge, Long> {
    // id 내림차순 첫 행이 최신이다 — append-only라 id 순서가 기록 순서다
    fun findFirstByOrderIdOrderByIdDesc(orderId: Long): OrderDispatchNudge?

    fun findAllByOrderIdOrderByIdDesc(orderId: Long): List<OrderDispatchNudge>
}

@Repository
class OrderDispatchNudgeRepositoryAdapter(
    private val jpaRepository: OrderDispatchNudgeJpaRepository,
) : OrderDispatchNudgeRepository {
    override fun save(nudge: OrderDispatchNudge): OrderDispatchNudge = jpaRepository.save(nudge)

    override fun findLatestByOrderId(orderId: Long): OrderDispatchNudge? = jpaRepository.findFirstByOrderIdOrderByIdDesc(orderId)

    override fun findAllByOrderIdDesc(orderId: Long): List<OrderDispatchNudge> = jpaRepository.findAllByOrderIdOrderByIdDesc(orderId)
}
