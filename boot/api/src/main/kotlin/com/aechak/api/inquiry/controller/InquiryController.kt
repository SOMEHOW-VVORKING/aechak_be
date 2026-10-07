package com.aechak.api.inquiry.controller

import com.aechak.api.inquiry.request.SubmitInquiryRequest
import com.aechak.api.inquiry.response.SubmitInquiryResponse
import com.aechak.application.inquiry.usecase.InquiryUseCase
import com.aechak.webcommon.response.ApiResponse
import com.aechak.websecurity.authentication.AuthPrincipal
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/inquiries")
class InquiryController(
    private val inquiryUseCase: InquiryUseCase,
) {
    @PostMapping
    fun submitInquiry(
        @Valid @RequestBody request: SubmitInquiryRequest,
        @AuthenticationPrincipal principal: AuthPrincipal,
    ): ResponseEntity<ApiResponse<SubmitInquiryResponse>> {
        val result = inquiryUseCase.submitInquiry(request.toCommand(principal.userId))
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.of(SubmitInquiryResponse.from(result)))
    }
}
