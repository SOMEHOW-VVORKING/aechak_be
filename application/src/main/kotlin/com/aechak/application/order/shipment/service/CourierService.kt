package com.aechak.application.order.shipment.service

import com.aechak.common.error.BusinessException
import com.aechak.domain.order.error.OrderErrorCode
import com.aechak.domain.order.shipment.CourierMaster
import com.aechak.domain.order.shipment.repository.CourierMasterRepository
import org.springframework.stereotype.Service

@Service
class CourierService(
    private val courierMasterRepository: CourierMasterRepository,
) {
    fun findActiveCouriers(): List<CourierMaster> = courierMasterRepository.findAllActive()

    fun verifyActiveCourier(courierCode: String) {
        val courier = courierMasterRepository.findByCourierCode(courierCode)
        if (courier == null || !courier.isActive) {
            throw BusinessException(OrderErrorCode.COURIER_NOT_FOUND)
        }
    }
}
