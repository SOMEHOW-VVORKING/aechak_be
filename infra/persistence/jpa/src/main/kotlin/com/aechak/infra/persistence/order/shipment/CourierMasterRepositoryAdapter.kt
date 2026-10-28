package com.aechak.infra.persistence.order.shipment

import com.aechak.domain.order.shipment.CourierMaster
import com.aechak.domain.order.shipment.repository.CourierMasterRepository
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository

/** PK가 courier_code(String)라 JpaRepository의 id 타입도 String이다. */
interface CourierMasterJpaRepository : JpaRepository<CourierMaster, String> {
    @Query("select c from CourierMaster c where c.isActive = true order by c.courierName asc, c.courierCode asc")
    fun findAllActive(): List<CourierMaster>

    @Query("select c from CourierMaster c where c.courierCode = :courierCode and c.isActive = true")
    fun findActiveByCourierCode(
        @Param("courierCode") courierCode: String,
    ): CourierMaster?
}

@Repository
class CourierMasterRepositoryAdapter(
    private val jpaRepository: CourierMasterJpaRepository,
) : CourierMasterRepository {
    override fun findAllActive(): List<CourierMaster> = jpaRepository.findAllActive()

    override fun findActiveByCourierCode(courierCode: String): CourierMaster? = jpaRepository.findActiveByCourierCode(courierCode)
}
