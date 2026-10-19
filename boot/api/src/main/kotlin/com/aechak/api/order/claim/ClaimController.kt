package com.aechak.api.order.claim

import com.aechak.api.order.claim.request.RequestClaimRequest
import com.aechak.api.order.claim.response.ClaimListResponse
import com.aechak.api.order.claim.response.RequestClaimResponse
import com.aechak.application.order.claim.usecase.ClaimUseCase
import com.aechak.application.order.claim.usecase.command.WithdrawClaimCommand
import com.aechak.webcommon.response.ApiResponse
import com.aechak.websecurity.authentication.AuthPrincipal
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/orders/{orderPublicId}/claims")
class ClaimController(
    private val claimUseCase: ClaimUseCase,
) {
    @PostMapping
    fun requestClaim(
        @PathVariable orderPublicId: String,
        @Valid @RequestBody request: RequestClaimRequest,
        @AuthenticationPrincipal principal: AuthPrincipal,
    ): ResponseEntity<ApiResponse<RequestClaimResponse>> {
        val result = claimUseCase.requestClaim(request.toCommand(principal.userId, orderPublicId))
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.of(RequestClaimResponse.from(result)))
    }

    @GetMapping
    fun getClaims(
        @PathVariable orderPublicId: String,
        @AuthenticationPrincipal principal: AuthPrincipal,
    ): ResponseEntity<ApiResponse<ClaimListResponse>> =
        ResponseEntity.ok(
            ApiResponse.of(ClaimListResponse.from(claimUseCase.getClaims(orderPublicId, principal.userId))),
        )

    @DeleteMapping("/{claimPublicId}")
    fun withdrawClaim(
        @PathVariable orderPublicId: String,
        @PathVariable claimPublicId: String,
        @AuthenticationPrincipal principal: AuthPrincipal,
    ): ResponseEntity<Void> {
        claimUseCase.withdrawClaim(
            WithdrawClaimCommand(
                buyerId = principal.userId,
                orderPublicId = orderPublicId,
                claimPublicId = claimPublicId,
            ),
        )
        return ResponseEntity.noContent().build()
    }
}
