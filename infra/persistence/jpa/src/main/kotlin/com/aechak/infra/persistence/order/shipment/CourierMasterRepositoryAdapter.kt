package com.aechak.infra.persistence.order.shipment

import com.aechak.domain.order.shipment.CourierMaster
import com.aechak.domain.order.shipment.repository.CourierMasterRepository
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.stereotype.Repository

interface CourierMasterJpaRepository : JpaRepository<CourierMaster, String> {
    @Query("select c from CourierMaster c where c.isActive = true order by c.courierName asc, c.courierCode asc")
    fun findAllActive(): List<CourierMaster>
}

@Repository
class CourierMasterRepositoryAdapter(
    private val jpaRepository: CourierMasterJpaRepository,
) : CourierMasterRepository {
    override fun findAllActive(): List<CourierMaster> = jpaRepository.findAllActive()
}
