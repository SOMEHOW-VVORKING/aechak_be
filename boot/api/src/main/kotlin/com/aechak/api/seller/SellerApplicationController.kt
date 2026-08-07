package com.aechak.api.seller

import com.aechak.api.seller.request.SaveDraftRequest
import com.aechak.api.seller.response.ApplicationResponse
import com.aechak.application.seller.usecase.SellerApplicationUseCase
import com.aechak.webcommon.response.ApiResponse
import com.aechak.websecurity.authentication.AuthPrincipal
import jakarta.validation.Valid
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/** 입점 신청(신청자 측) API — 전 EP ACTIVE 전용(UserStatusFilter). 유저당 신청서 1건이라 경로에 id가 없다. */
@RestController
@RequestMapping("/seller-applications")
class SellerApplicationController(
    private val sellerApplicationUseCase: SellerApplicationUseCase,
) {
    /** 신청 저장(임시저장) — 폼+서류 일괄. 응답은 저장된 신청서 전체(FE는 이걸로 화면 재구성). */
    @PostMapping
    fun saveDraft(
        @Valid @RequestBody request: SaveDraftRequest,
        @AuthenticationPrincipal principal: AuthPrincipal,
    ): ResponseEntity<ApiResponse<ApplicationResponse>> =
        ResponseEntity.ok(
            ApiResponse.of(ApplicationResponse.from(sellerApplicationUseCase.saveDraft(request.toCommand(principal.userId)))),
        )

    /** 내 신청 현황 — 심사중/승인/반려 화면 데이터. */
    @GetMapping("/me")
    fun getMe(
        @AuthenticationPrincipal principal: AuthPrincipal,
    ): ResponseEntity<ApiResponse<ApplicationResponse>> =
        ResponseEntity.ok(
            ApiResponse.of(ApplicationResponse.from(sellerApplicationUseCase.getMe(principal.userId))),
        )
}
