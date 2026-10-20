package com.aechak.seller.seller

import com.aechak.application.seller.usecase.SellerStoreUseCase
import com.aechak.seller.seller.request.StoreSettingsUpdateRequest
import com.aechak.seller.seller.response.StoreSettingsResponse
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

/** 셀러 스토어 설정 API — 대상은 항상 인증 주체의 스토어 1건이라 경로에 식별자가 없다. */
@RestController
@RequestMapping("/sellers/me/store")
class StoreSettingsController(
    private val sellerStoreUseCase: SellerStoreUseCase,
) {
    /** 스토어 설정 화면 데이터 — 셀러가 아닌 계정도 입점 신청 단계를 담아 200으로 받는다. */
    @GetMapping
    fun get(
        @AuthenticationPrincipal principal: AuthPrincipal,
    ): ResponseEntity<ApiResponse<StoreSettingsResponse>> =
        ResponseEntity.ok(
            ApiResponse.of(StoreSettingsResponse.from(sellerStoreUseCase.getStore(principal.userId))),
        )

    /** 전체 덮어쓰기 — 바디에 없는 항목은 비운 것으로 본다. */
    @PutMapping
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun update(
        @Valid @RequestBody request: StoreSettingsUpdateRequest,
        @AuthenticationPrincipal principal: AuthPrincipal,
    ): ResponseEntity<Void> {
        sellerStoreUseCase.updateStore(request.toCommand(principal.userId))
        return ResponseEntity.noContent().build()
    }
}
