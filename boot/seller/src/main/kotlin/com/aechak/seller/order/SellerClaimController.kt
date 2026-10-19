package com.aechak.seller.order

import com.aechak.application.order.claim.usecase.SellerClaimUseCase
import com.aechak.seller.order.request.ApproveClaimRequest
import com.aechak.seller.order.request.RejectClaimRequest
import com.aechak.seller.order.request.SellerClaimSearchRequest
import com.aechak.seller.order.request.UpdateClaimMemoRequest
import com.aechak.seller.order.response.SellerClaimDetailResponse
import com.aechak.seller.order.response.SellerClaimListResponse
import com.aechak.webcommon.response.ApiResponse
import com.aechak.websecurity.authentication.AuthPrincipal
import jakarta.validation.Valid
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.ModelAttribute
import org.springframework.web.bind.annotation.PatchMapping
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
    @GetMapping
    fun search(
        @Valid @ModelAttribute request: SellerClaimSearchRequest,
        @AuthenticationPrincipal principal: AuthPrincipal,
    ): ResponseEntity<ApiResponse<SellerClaimListResponse>> =
        ResponseEntity.ok(
            ApiResponse.of(SellerClaimListResponse.from(sellerClaimUseCase.searchClaims(request.toQuery(principal.userId)))),
        )

    @GetMapping("/{claimPublicId}")
    fun get(
        @PathVariable claimPublicId: String,
        @AuthenticationPrincipal principal: AuthPrincipal,
    ): ResponseEntity<ApiResponse<SellerClaimDetailResponse>> =
        ResponseEntity.ok(
            ApiResponse.of(SellerClaimDetailResponse.from(sellerClaimUseCase.getClaim(claimPublicId, principal.userId))),
        )

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

    /** 운영 메모는 내부용이라 구매자 응답에 나가지 않는다. */
    @PatchMapping("/{claimPublicId}/memo")
    fun updateMemo(
        @PathVariable claimPublicId: String,
        @Valid @RequestBody(required = false) request: UpdateClaimMemoRequest?,
        @AuthenticationPrincipal principal: AuthPrincipal,
    ): ResponseEntity<Void> {
        val body = request ?: UpdateClaimMemoRequest()
        sellerClaimUseCase.updateSellerMemo(body.toCommand(principal.userId, claimPublicId))
        return ResponseEntity.noContent().build()
    }
}
