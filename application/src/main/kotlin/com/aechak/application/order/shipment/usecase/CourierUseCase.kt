package com.aechak.application.order.shipment.usecase

import com.aechak.application.order.shipment.usecase.result.CourierResult

interface CourierUseCase {
    /** 송장 등록에 고를 수 있는 택배사 목록 — 활성 택배사만. */
    fun getCouriers(): List<CourierResult>
}
