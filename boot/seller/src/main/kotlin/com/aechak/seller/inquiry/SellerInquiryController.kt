package com.aechak.seller.inquiry

import com.aechak.application.inquiry.product.usecase.SellerProductInquiryUseCase
import com.aechak.seller.inquiry.request.AnswerInquiryRequest
import com.aechak.seller.inquiry.request.SellerInquirySearchRequest
import com.aechak.seller.inquiry.response.AnswerInquiryResponse
import com.aechak.seller.inquiry.response.SellerInquiryListResponse
import com.aechak.webcommon.response.ApiResponse
import com.aechak.websecurity.authentication.AuthPrincipal
import jakarta.validation.Valid
import org.springdoc.core.annotations.ParameterObject
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.ModelAttribute
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

/** 셀러가 받은 상품 문의 — 자기 상품에 온 것만 보이고 답할 수 있다. */
@RestController
@RequestMapping("/sellers/me/inquiries")
class SellerInquiryController(
    private val sellerProductInquiryUseCase: SellerProductInquiryUseCase,
) {
    @GetMapping
    fun search(
        @Valid @ModelAttribute @ParameterObject request: SellerInquirySearchRequest,
        @AuthenticationPrincipal principal: AuthPrincipal,
    ): ResponseEntity<ApiResponse<SellerInquiryListResponse>> =
        ResponseEntity.ok(
            ApiResponse.of(
                SellerInquiryListResponse.from(sellerProductInquiryUseCase.searchMyInquiries(request.toQuery(principal.userId))),
            ),
        )

    @PostMapping("/{inquiryId}/answer")
    @ResponseStatus(HttpStatus.CREATED)
    fun answer(
        @PathVariable("inquiryId") inquiryPublicId: String,
        @Valid @RequestBody request: AnswerInquiryRequest,
        @AuthenticationPrincipal principal: AuthPrincipal,
    ): ResponseEntity<ApiResponse<AnswerInquiryResponse>> {
        val result = sellerProductInquiryUseCase.answerProductInquiry(request.toCommand(principal.userId, inquiryPublicId))
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.of(AnswerInquiryResponse.from(result)))
    }
}
