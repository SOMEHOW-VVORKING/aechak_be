package com.aechak.seller.dashboard

import com.aechak.application.dashboard.seller.usecase.SellerDashboardUseCase
import com.aechak.seller.dashboard.request.SellerDashboardSummaryRequest
import com.aechak.seller.dashboard.response.SellerDashboardSummaryResponse
import com.aechak.webcommon.response.ApiResponse
import com.aechak.websecurity.authentication.AuthPrincipal
import jakarta.validation.Valid
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.ModelAttribute
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/sellers/me/dashboard")
class SellerDashboardController(
    private val sellerDashboardUseCase: SellerDashboardUseCase,
) {
    /** 셀러센터 첫 화면 집계 — 처리 대기 건수·정산 요약·상품 상태 요약을 한 번에 내린다. */
    @GetMapping("/summary")
    fun getSummary(
        @Valid @ModelAttribute request: SellerDashboardSummaryRequest,
        @AuthenticationPrincipal principal: AuthPrincipal,
    ): ResponseEntity<ApiResponse<SellerDashboardSummaryResponse>> =
        ResponseEntity.ok(
            ApiResponse.of(SellerDashboardSummaryResponse.from(sellerDashboardUseCase.getSummary(request.toQuery(principal.userId)))),
        )
}
