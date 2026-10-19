package com.aechak.domain.order.shipment.repository

import com.aechak.domain.order.shipment.CourierMaster

/** PK가 문자열 코드(courier_code) — 채번 없음. */
interface CourierMasterRepository {
    /** 활성 택배사를 이름 오름차순으로 반환한다. */
    fun findAllActive(): List<CourierMaster>

    fun findByCourierCode(courierCode: String): CourierMaster?
}
