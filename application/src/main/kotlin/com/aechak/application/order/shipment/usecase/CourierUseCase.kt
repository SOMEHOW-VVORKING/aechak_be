package com.aechak.application.order.shipment.usecase

import com.aechak.application.order.shipment.usecase.result.CourierResult

interface CourierUseCase {
    /** 송장 등록에 고를 수 있는 택배사 목록 — 활성 택배사만. */
    fun getCouriers(): List<CourierResult>

    /** 택배사 코드가 활성 마스터에 있는지 확인한다 — 없거나 비활성이면 예외. */
    fun verifyActiveCourier(courierCode: String)
}
