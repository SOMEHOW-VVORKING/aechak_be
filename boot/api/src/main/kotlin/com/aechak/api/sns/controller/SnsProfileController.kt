package com.aechak.api.sns.controller

import com.aechak.api.sns.response.SnsProfileResponse
import com.aechak.application.sns.usecase.SnsUseCase
import com.aechak.webcommon.response.ApiResponse
import com.aechak.websecurity.authentication.AuthPrincipal
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/sns")
class SnsProfileController(
    private val snsUseCase: SnsUseCase,
) {
    /** 비로그인도 부를 수 있어 principal이 null일 수 있다. */
    @GetMapping("/users/{publicId}")
    fun getProfile(
        @PathVariable publicId: String,
        @AuthenticationPrincipal principal: AuthPrincipal?,
    ): ResponseEntity<ApiResponse<SnsProfileResponse>> =
        ResponseEntity.ok(ApiResponse.of(SnsProfileResponse.from(snsUseCase.getProfile(publicId, principal?.userId))))

    @GetMapping("/me")
    fun getMyProfile(
        @AuthenticationPrincipal principal: AuthPrincipal,
    ): ResponseEntity<ApiResponse<SnsProfileResponse>> =
        ResponseEntity.ok(ApiResponse.of(SnsProfileResponse.from(snsUseCase.getMyProfile(principal.userId))))
}
