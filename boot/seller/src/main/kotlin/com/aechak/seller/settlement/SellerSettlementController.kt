package com.aechak.seller.settlement

import com.aechak.application.settlement.seller.usecase.SellerSettlementUseCase
import com.aechak.seller.settlement.request.SellerSettlementSearchRequest
import com.aechak.seller.settlement.response.SellerSettlementDetailResponse
import com.aechak.seller.settlement.response.SellerSettlementListResponse
import com.aechak.seller.settlement.response.SellerSettlementMonthsResponse
import com.aechak.webcommon.response.ApiResponse
import com.aechak.websecurity.authentication.AuthPrincipal
import jakarta.validation.Valid
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.ModelAttribute
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/sellers/me/settlements")
class SellerSettlementController(
    private val sellerSettlementUseCase: SellerSettlementUseCase,
) {
    /** 내 정산 목록 — 주문 하나가 한 행이다. 월·상태 필터 + 오프셋 페이지네이션 */
    @GetMapping
    fun getMySettlements(
        @Valid @ModelAttribute request: SellerSettlementSearchRequest,
        @AuthenticationPrincipal principal: AuthPrincipal,
    ): ResponseEntity<ApiResponse<SellerSettlementListResponse>> =
        ResponseEntity.ok(
            ApiResponse.of(
                SellerSettlementListResponse.from(sellerSettlementUseCase.searchSettlements(request.toQuery(principal.userId))),
            ),
        )

    /** 월 필터가 고를 수 있는 정산월 목록 — 정산 행이 있는 월만 최신순 */
    @GetMapping("/months")
    fun getMySettlementMonths(
        @AuthenticationPrincipal principal: AuthPrincipal,
    ): ResponseEntity<ApiResponse<SellerSettlementMonthsResponse>> =
        ResponseEntity.ok(
            ApiResponse.of(SellerSettlementMonthsResponse.from(sellerSettlementUseCase.getSettlementMonths(principal.userId))),
        )

    /** 내 정산 한 건 — 목록 행 클릭 시 상세용 */
    @GetMapping("/{orderPublicId}")
    fun getMySettlement(
        @PathVariable orderPublicId: String,
        @AuthenticationPrincipal principal: AuthPrincipal,
    ): ResponseEntity<ApiResponse<SellerSettlementDetailResponse>> =
        ResponseEntity.ok(
            ApiResponse.of(
                SellerSettlementDetailResponse.from(sellerSettlementUseCase.getSettlement(orderPublicId, principal.userId)),
            ),
        )
}
