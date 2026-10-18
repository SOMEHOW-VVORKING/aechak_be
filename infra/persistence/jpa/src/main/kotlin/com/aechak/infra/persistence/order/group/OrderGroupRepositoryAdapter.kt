package com.aechak.infra.persistence.order.group

import com.aechak.domain.order.group.OrderGroup
import com.aechak.domain.order.group.enums.OrderGroupStatus
import com.aechak.domain.order.group.repository.OrderGroupRepository
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Pageable
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

    @Modifying
    @Query(
        "update OrderGroup g " +
            "set g.status = :next, g.updatedAt = :now " +
            "where g.id = :id and g.status = :expected",
    )
    fun transitionStatus(
        @Param("id") id: Long,
        @Param("expected") expected: OrderGroupStatus,
        @Param("next") next: OrderGroupStatus,
        @Param("now") now: LocalDateTime,
    ): Int

    @Query(
        "select g from OrderGroup g " +
            "where g.status = :status and g.expiresAt < :now " +
            "order by g.expiresAt asc",
    )
    fun findExpired(
        @Param("status") status: OrderGroupStatus,
        @Param("now") now: LocalDateTime,
        pageable: Pageable,
    ): List<OrderGroup>
}

@Repository
class OrderGroupRepositoryAdapter(
    private val jpaRepository: OrderGroupJpaRepository,
) : OrderGroupRepository {
    override fun save(orderGroup: OrderGroup): OrderGroup = jpaRepository.save(orderGroup)

    override fun findById(id: Long): OrderGroup? = jpaRepository.findByIdOrNull(id)

    override fun findByIdempotencyKey(idempotencyKey: String): OrderGroup? = jpaRepository.findByIdempotencyKey(idempotencyKey)

    override fun findByPublicId(publicId: String): OrderGroup? = jpaRepository.findByPublicId(publicId)

    // 벌크 JPQL은 @PreUpdate를 우회하므로 updated_at을 쿼리에서 함께 SET한다
    override fun markPaidIfPending(id: Long): Boolean =
        jpaRepository.transitionStatus(
            id = id,
            expected = OrderGroupStatus.PENDING_PAYMENT,
            next = OrderGroupStatus.PAID,
            now = LocalDateTime.now(),
        ) == 1

    override fun cancelIfPending(id: Long): Boolean =
        jpaRepository.transitionStatus(
            id = id,
            expected = OrderGroupStatus.PENDING_PAYMENT,
            next = OrderGroupStatus.CANCELLED,
            now = LocalDateTime.now(),
        ) == 1

    // Pageable 등 Spring 타입을 포트로 역류시키지 않는다 — 포트는 limit만 받는다
    override fun findExpiredPending(
        now: LocalDateTime,
        limit: Int,
    ): List<OrderGroup> = jpaRepository.findExpired(OrderGroupStatus.PENDING_PAYMENT, now, PageRequest.of(0, limit))
}
