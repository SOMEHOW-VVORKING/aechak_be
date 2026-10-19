package com.aechak.infra.persistence.order.shipment

import com.aechak.domain.order.shipment.CourierMaster
import com.aechak.domain.order.shipment.repository.CourierMasterRepository
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

/** PK가 courier_code(String)라 JpaRepository의 id 타입도 String이다. */
interface CourierMasterJpaRepository : JpaRepository<CourierMaster, String>

@Repository
class CourierMasterRepositoryAdapter(
    private val jpaRepository: CourierMasterJpaRepository,
) : CourierMasterRepository {
    override fun findByCourierCode(courierCode: String): CourierMaster? = jpaRepository.findById(courierCode).orElse(null)
}
