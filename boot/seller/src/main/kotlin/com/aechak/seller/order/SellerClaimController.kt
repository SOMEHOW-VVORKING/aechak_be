package com.aechak.seller.order

import com.aechak.application.order.claim.usecase.SellerClaimUseCase
import com.aechak.seller.order.request.ApproveClaimRequest
import com.aechak.seller.order.request.RejectClaimRequest
import com.aechak.websecurity.authentication.AuthPrincipal
import jakarta.validation.Valid
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/sellers/me/claims")
class SellerClaimController(
    private val sellerClaimUseCase: SellerClaimUseCase,
) {
    /** 승인은 상태만 바꾼다 — 환불 집행은 별도 경로다. */
    @PostMapping("/{claimPublicId}/approve")
    fun approve(
        @PathVariable claimPublicId: String,
        @Valid @RequestBody(required = false) request: ApproveClaimRequest?,
        @AuthenticationPrincipal principal: AuthPrincipal,
    ): ResponseEntity<Void> {
        val body = request ?: ApproveClaimRequest()
        sellerClaimUseCase.approveClaim(body.toCommand(principal.userId, claimPublicId))
        return ResponseEntity.noContent().build()
    }

    @PostMapping("/{claimPublicId}/reject")
    fun reject(
        @PathVariable claimPublicId: String,
        @Valid @RequestBody(required = false) request: RejectClaimRequest?,
        @AuthenticationPrincipal principal: AuthPrincipal,
    ): ResponseEntity<Void> {
        val body = request ?: RejectClaimRequest()
        sellerClaimUseCase.rejectClaim(body.toCommand(principal.userId, claimPublicId))
        return ResponseEntity.noContent().build()
    }
}
