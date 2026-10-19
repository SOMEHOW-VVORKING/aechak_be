package com.aechak.infra.persistence.order.shipment

import com.aechak.domain.order.order.enums.OrderStatus
import com.aechak.domain.order.shipment.Shipment
import com.aechak.domain.order.shipment.enums.ShipmentType
import com.aechak.domain.order.shipment.repository.ShipmentRepository
import org.springframework.data.domain.Limit
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository
import java.time.LocalDateTime

interface ShipmentJpaRepository : JpaRepository<Shipment, Long> {
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
}

@Repository
class ShipmentRepositoryAdapter(
    private val jpaRepository: ShipmentJpaRepository,
) : ShipmentRepository {
    override fun save(shipment: Shipment): Shipment = jpaRepository.save(shipment)

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
}
