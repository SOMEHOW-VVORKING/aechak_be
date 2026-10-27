package com.aechak.api.order.claim

import com.aechak.api.order.claim.response.ClaimReasonCatalogResponse
import com.aechak.application.order.claim.usecase.ClaimUseCase
import com.aechak.webcommon.response.ApiResponse
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/** 클레임 유형별 사유 선택지 조회. */
@RestController
@RequestMapping("/claims/reasons")
class ClaimReasonController(
    private val claimUseCase: ClaimUseCase,
) {
    @GetMapping
    fun getClaimReasons(): ResponseEntity<ApiResponse<ClaimReasonCatalogResponse>> =
        ResponseEntity.ok(ApiResponse.of(ClaimReasonCatalogResponse.from(claimUseCase.getClaimReasons())))
}
