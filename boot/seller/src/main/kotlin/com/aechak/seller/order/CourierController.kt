package com.aechak.seller.order

import com.aechak.application.order.shipment.usecase.CourierUseCase
import com.aechak.seller.order.response.CourierListResponse
import com.aechak.webcommon.response.ApiResponse
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/** 경로가 /sellers 로 시작해야 FE가 셀러센터 호스트로 보낸다 — 호스트 분기는 경로 접두로만 판정된다. */
@RestController
@RequestMapping("/sellers/couriers")
class CourierController(
    private val courierUseCase: CourierUseCase,
) {
    @GetMapping
    fun getCouriers(): ResponseEntity<ApiResponse<CourierListResponse>> =
        ResponseEntity.ok(ApiResponse.of(CourierListResponse.from(courierUseCase.getCouriers())))
}
