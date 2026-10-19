package com.aechak.infra.persistence.order.order

import com.aechak.domain.order.order.Order
import com.aechak.domain.order.order.enums.OrderStatus
import com.aechak.domain.order.order.repository.OrderRepository
import jakarta.persistence.LockModeType
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.findByIdOrNull
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository
import java.time.LocalDateTime

interface OrderJpaRepository : JpaRepository<Order, Long> {
    fun findAllByOrderGroupIdOrderByIdAsc(orderGroupId: Long): List<Order>

    // 그룹과 품목을 함께 끌어온다 — 클레임 목록의 환불액 재계산이 주문마다 추가 질의를 내지 않게.
    @Query(
        "select distinct o from Order o " +
            "join fetch o.orderGroup g " +
            "left join fetch o._items " +
            "where g.id in :orderGroupIds",
    )
    fun findAllWithItemsByOrderGroupIdIn(
        @Param("orderGroupIds") orderGroupIds: Collection<Long>,
    ): List<Order>

    fun findByPublicIdAndSellerId(
        publicId: String,
        sellerId: Long,
    ): Order?

    fun findByPublicIdAndOrderGroupBuyerId(
        publicId: String,
        buyerId: Long,
    ): Order?

    // 조인을 끼우면 MySQL이 조인한 주문그룹 행까지 잠근다 — 주문 행만 잠그도록 단일 테이블로 둔다
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select o from Order o where o.publicId = :publicId")
    fun findForUpdateByPublicId(
        @Param("publicId") publicId: String,
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
}

@Repository
class OrderRepositoryAdapter(
    private val jpaRepository: OrderJpaRepository,
) : OrderRepository {
    override fun saveAll(orders: List<Order>): List<Order> = jpaRepository.saveAll(orders)

    override fun findById(id: Long): Order? = jpaRepository.findByIdOrNull(id)

    override fun findAllByOrderGroupId(orderGroupId: Long): List<Order> = jpaRepository.findAllByOrderGroupIdOrderByIdAsc(orderGroupId)

    override fun findAllByOrderGroupIdIn(orderGroupIds: Collection<Long>): List<Order> =
        if (orderGroupIds.isEmpty()) emptyList() else jpaRepository.findAllWithItemsByOrderGroupIdIn(orderGroupIds)

    override fun findByPublicIdAndBuyerId(
        publicId: String,
        buyerId: Long,
    ): Order? = jpaRepository.findByPublicIdAndOrderGroupBuyerId(publicId, buyerId)

    override fun findByPublicIdForUpdate(publicId: String): Order? = jpaRepository.findForUpdateByPublicId(publicId)

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
}
