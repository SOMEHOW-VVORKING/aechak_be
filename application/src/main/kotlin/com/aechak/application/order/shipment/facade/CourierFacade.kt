package com.aechak.application.order.shipment.facade

import com.aechak.application.order.shipment.service.CourierService
import com.aechak.application.order.shipment.usecase.CourierUseCase
import com.aechak.application.order.shipment.usecase.result.CourierResult
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class CourierFacade(
    private val courierService: CourierService,
) : CourierUseCase {
    @Transactional(readOnly = true)
    override fun getCouriers(): List<CourierResult> = courierService.findActiveCouriers().map(CourierResult::from)
}
