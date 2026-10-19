package com.aechak.infra.persistence.order.order

import com.aechak.domain.order.claim.enums.ClaimStatus
import com.aechak.domain.order.claim.policy.ClaimProgressPolicy
import com.aechak.domain.order.order.Order
import com.aechak.domain.order.order.enums.OrderStatus
import com.aechak.domain.order.order.repository.OrderRepository
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository
import java.time.LocalDateTime

interface OrderJpaRepository : JpaRepository<Order, Long> {
    fun findAllByOrderGroupId(orderGroupId: Long): List<Order>

    fun findByPublicIdAndSellerId(
        publicId: String,
        sellerId: Long,
    ): Order?

    fun findByPublicIdAndOrderGroupBuyerId(
        publicId: String,
        buyerId: Long,
    ): Order?

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

    @Modifying
    @Query(
        "update Order o " +
            "set o.status = :next, o.updatedAt = :now " +
            "where o.id = :id and o.status = :expected",
    )
    fun transitionStatus(
        @Param("id") id: Long,
        @Param("expected") expected: OrderStatus,
        @Param("next") next: OrderStatus,
        @Param("now") now: LocalDateTime,
    ): Int

    @Modifying
    @Query(
        "update Order o " +
            "set o.purchaseConfirmNotifiedAt = :notifiedAt, o.updatedAt = :now " +
            "where o.id = :id and o.status = :status and o.purchaseConfirmNotifiedAt is null",
    )
    fun markConfirmNotified(
        @Param("id") id: Long,
        @Param("status") status: OrderStatus,
        @Param("notifiedAt") notifiedAt: LocalDateTime,
        @Param("now") now: LocalDateTime,
    ): Int

    @Query(
        "select o.id from Order o " +
            "where o.status = :status and o.autoConfirmDueAt <= :dueAtOrBefore and o.id > :afterId " +
            "and not exists (select c.id from Claim c where c.orderId = o.id and c.status in :claimStatuses) " +
            "order by o.id asc",
    )
    fun findAutoConfirmDueIds(
        @Param("status") status: OrderStatus,
        @Param("dueAtOrBefore") dueAtOrBefore: LocalDateTime,
        @Param("afterId") afterId: Long,
        @Param("claimStatuses") claimStatuses: Collection<ClaimStatus>,
        pageable: Pageable,
    ): List<Long>

    @Query(
        "select o.id from Order o " +
            "where o.status = :status and o.purchaseConfirmNotifiedAt is null " +
            "and o.autoConfirmDueAt > :dueAtAfter and o.autoConfirmDueAt <= :dueAtOrBefore and o.id > :afterId " +
            "order by o.id asc",
    )
    fun findConfirmNotifyTargetIds(
        @Param("status") status: OrderStatus,
        @Param("dueAtAfter") dueAtAfter: LocalDateTime,
        @Param("dueAtOrBefore") dueAtOrBefore: LocalDateTime,
        @Param("afterId") afterId: Long,
        pageable: Pageable,
    ): List<Long>
}

@Repository
class OrderRepositoryAdapter(
    private val jpaRepository: OrderJpaRepository,
) : OrderRepository {
    override fun saveAll(orders: List<Order>): List<Order> = jpaRepository.saveAll(orders)

    override fun findById(id: Long): Order? = jpaRepository.findById(id).orElse(null)

    override fun findAllByOrderGroupId(orderGroupId: Long): List<Order> = jpaRepository.findAllByOrderGroupId(orderGroupId)

    override fun findByPublicIdAndBuyerId(
        publicId: String,
        buyerId: Long,
    ): Order? = jpaRepository.findByPublicIdAndOrderGroupBuyerId(publicId, buyerId)

    override fun findByPublicIdAndSellerId(
        publicId: String,
        sellerId: Long,
    ): Order? = jpaRepository.findByPublicIdAndSellerId(publicId, sellerId)

    // 벌크 JPQL은 @PreUpdate를 우회하므로 updated_at을 쿼리에서 함께 SET한다
    override fun markAllPaidByOrderGroupId(orderGroupId: Long): Int =
        jpaRepository.transitionAllStatus(
            groupId = orderGroupId,
            expected = OrderStatus.PENDING_PAYMENT,
            next = OrderStatus.PAID,
            now = LocalDateTime.now(),
        )

    // 벌크 JPQL은 @PreUpdate를 우회하므로 updated_at을 쿼리에서 함께 SET한다
    override fun transitionStatusIf(
        id: Long,
        expected: OrderStatus,
        next: OrderStatus,
    ): Boolean =
        jpaRepository.transitionStatus(
            id = id,
            expected = expected,
            next = next,
            now = LocalDateTime.now(),
        ) == 1

    // 클레임 유보 판정 기준은 도메인 정책이 쥔다 — 자동확정 스캔과 단건 재확인이 같은 상태 집합을 본다
    override fun findAutoConfirmDueIds(
        dueAtOrBefore: LocalDateTime,
        afterId: Long,
        limit: Int,
    ): List<Long> =
        jpaRepository.findAutoConfirmDueIds(
            status = OrderStatus.DELIVERED,
            dueAtOrBefore = dueAtOrBefore,
            afterId = afterId,
            claimStatuses = ClaimProgressPolicy.IN_PROGRESS,
            pageable = PageRequest.of(0, limit),
        )

    // 안내 컬럼만 조건부로 바꾼다 — 엔티티 갱신은 전 컬럼을 덮어 그 사이 커밋된 구매확정을 되돌린다
    override fun markConfirmNotifiedIf(
        id: Long,
        notifiedAt: LocalDateTime,
    ): Boolean =
        jpaRepository.markConfirmNotified(
            id = id,
            status = OrderStatus.DELIVERED,
            notifiedAt = notifiedAt,
            now = LocalDateTime.now(),
        ) == 1

    override fun findConfirmNotifyTargetIds(
        dueAtAfter: LocalDateTime,
        dueAtOrBefore: LocalDateTime,
        afterId: Long,
        limit: Int,
    ): List<Long> =
        jpaRepository.findConfirmNotifyTargetIds(
            status = OrderStatus.DELIVERED,
            dueAtAfter = dueAtAfter,
            dueAtOrBefore = dueAtOrBefore,
            afterId = afterId,
            pageable = PageRequest.of(0, limit),
        )
}
