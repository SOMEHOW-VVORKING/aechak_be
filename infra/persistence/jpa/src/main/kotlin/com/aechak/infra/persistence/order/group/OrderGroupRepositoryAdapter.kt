package com.aechak.infra.persistence.order.group

import com.aechak.domain.order.group.OrderGroup
import com.aechak.domain.order.group.enums.OrderGroupStatus
import com.aechak.domain.order.group.repository.ExpiredPendingOrderGroup
import com.aechak.domain.order.group.repository.OrderGroupRepository
import org.springframework.data.domain.Limit
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.findByIdOrNull
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository
import java.time.LocalDateTime

interface OrderGroupJpaRepository : JpaRepository<OrderGroup, Long> {
    fun findByIdempotencyKey(idempotencyKey: String): OrderGroup?

    fun findByPublicId(publicId: String): OrderGroup?

    @Query(
        "select new com.aechak.domain.order.group.repository.ExpiredPendingOrderGroup(g.id, g.publicId, g.expiresAt) " +
            "from OrderGroup g " +
            "where g.status = :status and g.expiresAt < :now " +
            "order by g.expiresAt asc, g.id asc",
    )
    fun findExpiredTargets(
        @Param("status") status: OrderGroupStatus,
        @Param("now") now: LocalDateTime,
        limit: Limit,
    ): List<ExpiredPendingOrderGroup>

    @Query(
        "select new com.aechak.domain.order.group.repository.ExpiredPendingOrderGroup(g.id, g.publicId, g.expiresAt) " +
            "from OrderGroup g " +
            "where g.status = :status and g.expiresAt < :now " +
            "and (g.expiresAt > :afterExpiresAt or (g.expiresAt = :afterExpiresAt and g.id > :afterId)) " +
            "order by g.expiresAt asc, g.id asc",
    )
    fun findExpiredTargetsAfter(
        @Param("status") status: OrderGroupStatus,
        @Param("now") now: LocalDateTime,
        @Param("afterExpiresAt") afterExpiresAt: LocalDateTime,
        @Param("afterId") afterId: Long,
        limit: Limit,
    ): List<ExpiredPendingOrderGroup>

    @Modifying
    @Query(
        "update OrderGroup g set g.status = :next, g.updatedAt = :now " +
            "where g.id = :id and g.status = :current",
    )
    fun updateStatusIf(
        @Param("id") id: Long,
        @Param("current") current: OrderGroupStatus,
        @Param("next") next: OrderGroupStatus,
        @Param("now") now: LocalDateTime,
    ): Int
}

@Repository
class OrderGroupRepositoryAdapter(
    private val jpaRepository: OrderGroupJpaRepository,
) : OrderGroupRepository {
    override fun save(orderGroup: OrderGroup): OrderGroup = jpaRepository.save(orderGroup)

    override fun findByIdempotencyKey(idempotencyKey: String): OrderGroup? = jpaRepository.findByIdempotencyKey(idempotencyKey)

    override fun findByPublicId(publicId: String): OrderGroup? = jpaRepository.findByPublicId(publicId)

    override fun findById(id: Long): OrderGroup? = jpaRepository.findByIdOrNull(id)

    override fun findExpiredPendingTargets(
        now: LocalDateTime,
        after: ExpiredPendingOrderGroup?,
        limit: Int,
    ): List<ExpiredPendingOrderGroup> =
        if (after == null) {
            jpaRepository.findExpiredTargets(OrderGroupStatus.PENDING_PAYMENT, now, Limit.of(limit))
        } else {
            jpaRepository.findExpiredTargetsAfter(OrderGroupStatus.PENDING_PAYMENT, now, after.expiresAt, after.id, Limit.of(limit))
        }

    // 벌크 JPQL은 @PreUpdate를 우회하므로 updated_at을 쿼리에서 함께 SET함
    override fun cancelIfPending(id: Long): Boolean =
        jpaRepository.updateStatusIf(
            id,
            OrderGroupStatus.PENDING_PAYMENT,
            OrderGroupStatus.CANCELLED,
            LocalDateTime.now(),
        ) == 1
}
