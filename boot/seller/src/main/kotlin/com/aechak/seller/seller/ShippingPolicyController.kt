package com.aechak.seller.seller

import com.aechak.application.seller.usecase.SellerShippingPolicyUseCase
import com.aechak.seller.seller.request.ShippingPolicyUpdateRequest
import com.aechak.seller.seller.response.ShippingPolicyResponse
import com.aechak.webcommon.response.ApiResponse
import com.aechak.websecurity.authentication.AuthPrincipal
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

/** 셀러 배송 정책 API — 대상은 항상 인증 주체의 정책 1건이라 경로에 식별자가 없다. */
@RestController
@RequestMapping("/sellers/me/shipping-policy")
class ShippingPolicyController(
    private val sellerShippingPolicyUseCase: SellerShippingPolicyUseCase,
) {
    @GetMapping
    fun get(
        @AuthenticationPrincipal principal: AuthPrincipal,
    ): ResponseEntity<ApiResponse<ShippingPolicyResponse>> =
        ResponseEntity.ok(
            ApiResponse.of(ShippingPolicyResponse.from(sellerShippingPolicyUseCase.getShippingPolicy(principal.userId))),
        )

    /** 전체 덮어쓰기 — 바디에 없는 항목은 비운 것으로 본다. */
    @PutMapping
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun update(
        @Valid @RequestBody request: ShippingPolicyUpdateRequest,
        @AuthenticationPrincipal principal: AuthPrincipal,
    ): ResponseEntity<Void> {
        sellerShippingPolicyUseCase.updateShippingPolicy(request.toCommand(principal.userId))
        return ResponseEntity.noContent().build()
    }
}
