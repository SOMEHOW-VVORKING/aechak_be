package com.aechak.domain.order.shipment.repository

import com.aechak.domain.order.shipment.CourierMaster

/** PK가 문자열 코드(courier_code) — 채번 없음. */
interface CourierMasterRepository {
    fun findActiveByCourierCode(courierCode: String): CourierMaster?
}
