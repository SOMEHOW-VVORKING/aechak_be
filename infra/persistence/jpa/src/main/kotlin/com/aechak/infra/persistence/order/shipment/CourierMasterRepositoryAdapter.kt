package com.aechak.infra.persistence.order.shipment

import com.aechak.domain.order.shipment.CourierMaster
import com.aechak.domain.order.shipment.repository.CourierMasterRepository
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.stereotype.Repository

interface CourierMasterJpaRepository : JpaRepository<CourierMaster, String> {
    // 이름이 같은 택배사가 들어와도 순서가 흔들리지 않게 코드를 2차 정렬 키로 둔다
    @Query("select c from CourierMaster c where c.isActive = true order by c.courierName asc, c.courierCode asc")
    fun findAllActive(): List<CourierMaster>
}

@Repository
class CourierMasterRepositoryAdapter(
    private val jpaRepository: CourierMasterJpaRepository,
) : CourierMasterRepository {
    override fun findAllActive(): List<CourierMaster> = jpaRepository.findAllActive()
}
