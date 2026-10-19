package com.aechak.infra.persistence.order.shipment

import com.aechak.domain.order.shipment.CourierMaster
import com.aechak.domain.order.shipment.Shipment
import com.aechak.domain.order.shipment.enums.ShipmentType
import com.aechak.domain.order.shipment.repository.CourierMasterRepository
import com.aechak.domain.order.shipment.repository.ShipmentRepository
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

interface ShipmentJpaRepository : JpaRepository<Shipment, Long> {
    fun findByClaimIdAndShipmentType(
        claimId: Long,
        shipmentType: ShipmentType,
    ): Shipment?
}

interface CourierMasterJpaRepository : JpaRepository<CourierMaster, String> {
    fun findByCourierCodeAndIsActiveTrue(courierCode: String): CourierMaster?
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
}

@Repository
class CourierMasterRepositoryAdapter(
    private val jpaRepository: CourierMasterJpaRepository,
) : CourierMasterRepository {
    override fun findActiveByCourierCode(courierCode: String): CourierMaster? = jpaRepository.findByCourierCodeAndIsActiveTrue(courierCode)
}
