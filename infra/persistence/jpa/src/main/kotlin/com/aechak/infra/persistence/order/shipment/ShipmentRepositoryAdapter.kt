package com.aechak.infra.persistence.order.shipment

import com.aechak.domain.order.order.enums.OrderStatus
import com.aechak.domain.order.shipment.Shipment
import com.aechak.domain.order.shipment.enums.ShipmentType
import com.aechak.domain.order.shipment.repository.ShipmentRepository
import org.springframework.data.domain.Limit
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository
import java.time.LocalDateTime

interface ShipmentJpaRepository : JpaRepository<Shipment, Long> {
    // 추적 두 컬럼만 짚어 쓴다 — 더티체킹 UPDATE에 실으면 배송완료 전이와 서로의 컬럼을 덮는다
    @Modifying
    @Query(
        "update Shipment s " +
            "set s.lastTrackingStatus = :lastTrackingStatus, s.lastSyncedAt = :syncedAt, s.updatedAt = :syncedAt " +
            "where s.id = :id",
    )
    fun updateTracking(
        @Param("id") id: Long,
        @Param("lastTrackingStatus") lastTrackingStatus: String,
        @Param("syncedAt") syncedAt: LocalDateTime,
    ): Int

    fun findByClaimIdAndShipmentType(
        claimId: Long,
        shipmentType: ShipmentType,
    ): Shipment?

    @Query("select s from Shipment s where s.order.id = :orderId and s.shipmentType = :shipmentType")
    fun findByOrderIdAndShipmentType(
        @Param("orderId") orderId: Long,
        @Param("shipmentType") shipmentType: ShipmentType,
    ): Shipment?

    // 주문을 함께 읽는다 — 호출부가 주문 상태와 공개 식별자를 보므로 지연 로딩이면 건당 추가 쿼리가 된다
    @Query(
        "select s from Shipment s join fetch s.order o " +
            "where s.shipmentType = :shipmentType and s.deliveredAt is null " +
            "and s.dispatchedAt < :dispatchedBefore and o.status = :status " +
            "and s.id > :afterShipmentId " +
            "order by s.id asc",
    )
    fun findPendingDispatchedBefore(
        @Param("shipmentType") shipmentType: ShipmentType,
        @Param("dispatchedBefore") dispatchedBefore: LocalDateTime,
        @Param("status") status: OrderStatus,
        @Param("afterShipmentId") afterShipmentId: Long,
        limit: Limit,
    ): List<Shipment>

    // 택배사와 주문을 함께 읽는다 — 호출부가 택배사 코드와 주문 공개 식별자를 보므로 지연 로딩이면 건당 추가 쿼리가 된다
    @Query(
        "select s from Shipment s join fetch s.courier join fetch s.order o " +
            "where s.shipmentType = :shipmentType and s.deliveredAt is null " +
            "and s.dispatchedAt is not null and o.status = :status " +
            "and s.id > :afterShipmentId " +
            "order by s.id asc",
    )
    fun findTrackingTargets(
        @Param("shipmentType") shipmentType: ShipmentType,
        @Param("status") status: OrderStatus,
        @Param("afterShipmentId") afterShipmentId: Long,
        limit: Limit,
    ): List<Shipment>
}

@Repository
class ShipmentRepositoryAdapter(
    private val jpaRepository: ShipmentJpaRepository,
) : ShipmentRepository {
    override fun save(shipment: Shipment): Shipment = jpaRepository.save(shipment)

    override fun updateTracking(
        shipmentId: Long,
        lastTrackingStatus: String,
        syncedAt: LocalDateTime,
    ): Int = jpaRepository.updateTracking(shipmentId, lastTrackingStatus, syncedAt)

    override fun findByClaimIdAndShipmentType(
        claimId: Long,
        shipmentType: ShipmentType,
    ): Shipment? = jpaRepository.findByClaimIdAndShipmentType(claimId, shipmentType)

    override fun findByOrderIdAndShipmentType(
        orderId: Long,
        shipmentType: ShipmentType,
    ): Shipment? = jpaRepository.findByOrderIdAndShipmentType(orderId, shipmentType)

    override fun findPendingDispatchedBefore(
        dispatchedBefore: LocalDateTime,
        afterShipmentId: Long,
        limit: Int,
    ): List<Shipment> =
        jpaRepository.findPendingDispatchedBefore(
            shipmentType = ShipmentType.INITIAL,
            dispatchedBefore = dispatchedBefore,
            status = OrderStatus.SHIPPING,
            afterShipmentId = afterShipmentId,
            limit = Limit.of(limit),
        )

    override fun findTrackingTargets(
        afterShipmentId: Long,
        limit: Int,
    ): List<Shipment> =
        jpaRepository.findTrackingTargets(
            shipmentType = ShipmentType.INITIAL,
            status = OrderStatus.SHIPPING,
            afterShipmentId = afterShipmentId,
            limit = Limit.of(limit),
        )
}
