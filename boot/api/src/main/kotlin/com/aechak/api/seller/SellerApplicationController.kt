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

/** 입점 신청(신청자 측) API — 전 EP ACTIVE 전용(UserStatusFilter). 대상은 항상 인증 주체의 신청서 1건이라 경로에 식별자가 없다. */
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

    /** 신청 제출 — 유형별 필수 정보·서류 검증 후 심사 대기로 전환. 이후 수정·서류 잠금. */
    @PostMapping("/submit")
    fun submit(
        @AuthenticationPrincipal principal: AuthPrincipal,
    ): ResponseEntity<Void> {
        sellerApplicationUseCase.submit(principal.userId)
        return ResponseEntity.noContent().build()
    }

    /** 내 신청 현황 — 심사중/승인/반려 화면 데이터. */
    @GetMapping
    fun getMe(
        @AuthenticationPrincipal principal: AuthPrincipal,
    ): ResponseEntity<ApiResponse<ApplicationResponse>> =
        ResponseEntity.ok(
            ApiResponse.of(ApplicationResponse.from(sellerApplicationUseCase.getMe(principal.userId))),
        )
}
