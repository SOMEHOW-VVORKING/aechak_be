package com.aechak.infra.persistence.order.shipment

import com.aechak.domain.order.shipment.Shipment
import com.aechak.domain.order.shipment.enums.ShipmentType
import com.aechak.domain.order.shipment.repository.ShipmentRepository
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository

interface ShipmentJpaRepository : JpaRepository<Shipment, Long> {
    @Query("select s from Shipment s where s.order.id = :orderId and s.shipmentType = :shipmentType")
    fun findByOrderIdAndShipmentType(
        @Param("orderId") orderId: Long,
        @Param("shipmentType") shipmentType: ShipmentType,
    ): Shipment?
}

@Repository
class ShipmentRepositoryAdapter(
    private val jpaRepository: ShipmentJpaRepository,
) : ShipmentRepository {
    override fun save(shipment: Shipment): Shipment = jpaRepository.save(shipment)

    override fun findByOrderIdAndShipmentType(
        orderId: Long,
        shipmentType: ShipmentType,
    ): Shipment? = jpaRepository.findByOrderIdAndShipmentType(orderId, shipmentType)
}
