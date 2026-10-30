package com.aechak.api.inquiry.controller

import com.aechak.api.inquiry.request.AskProductInquiryRequest
import com.aechak.api.inquiry.request.MyProductInquiryListRequest
import com.aechak.api.inquiry.request.ProductInquiryListRequest
import com.aechak.api.inquiry.response.AskProductInquiryResponse
import com.aechak.api.inquiry.response.MyProductInquiryListResponse
import com.aechak.api.inquiry.response.ProductInquiryListResponse
import com.aechak.application.inquiry.product.usecase.ProductInquiryUseCase
import com.aechak.application.inquiry.product.usecase.command.WithdrawProductInquiryCommand
import com.aechak.webcommon.response.ApiResponse
import com.aechak.websecurity.authentication.AuthPrincipal
import jakarta.validation.Valid
import org.springdoc.core.annotations.ParameterObject
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.ModelAttribute
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

/**
 * 상품 문의 — 구매자 경로.
 * 목록도 로그인을 요구한다: 비밀글 가리기 판정에 조회자가 필요하고, 같은 자리의 리뷰 목록과 기준을 맞춘다.
 */
@RestController
class ProductInquiryController(
    private val productInquiryUseCase: ProductInquiryUseCase,
) {
    @PostMapping("/products/{productId}/inquiries")
    @ResponseStatus(HttpStatus.CREATED)
    fun askProductInquiry(
        @PathVariable("productId") productPublicId: String,
        @Valid @RequestBody request: AskProductInquiryRequest,
        @AuthenticationPrincipal principal: AuthPrincipal,
    ): ResponseEntity<ApiResponse<AskProductInquiryResponse>> {
        val result = productInquiryUseCase.askProductInquiry(request.toCommand(principal.userId, productPublicId))
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.of(AskProductInquiryResponse.from(result)))
    }

    @GetMapping("/products/{productId}/inquiries")
    fun getProductInquiries(
        @PathVariable("productId") productPublicId: String,
        @Valid @ModelAttribute @ParameterObject request: ProductInquiryListRequest,
        @AuthenticationPrincipal principal: AuthPrincipal,
    ): ResponseEntity<ApiResponse<ProductInquiryListResponse>> =
        ResponseEntity.ok(
            ApiResponse.of(
                ProductInquiryListResponse.from(
                    productInquiryUseCase.getProductInquiries(request.toQuery(productPublicId), principal.userId),
                ),
            ),
        )

    @GetMapping("/users/me/product-inquiries")
    fun getMyProductInquiries(
        @Valid @ModelAttribute @ParameterObject request: MyProductInquiryListRequest,
        @AuthenticationPrincipal principal: AuthPrincipal,
    ): ResponseEntity<ApiResponse<MyProductInquiryListResponse>> =
        ResponseEntity.ok(
            ApiResponse.of(
                MyProductInquiryListResponse.from(productInquiryUseCase.getMyProductInquiries(request.toQuery(principal.userId))),
            ),
        )

    /** 철회. 답변이 달린 뒤에는 거절한다 — 셀러가 쓴 글이 사라지면 안 된다. */
    @DeleteMapping("/product-inquiries/{inquiryId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun withdrawProductInquiry(
        @PathVariable("inquiryId") inquiryPublicId: String,
        @AuthenticationPrincipal principal: AuthPrincipal,
    ): ResponseEntity<Void> {
        productInquiryUseCase.withdrawProductInquiry(
            WithdrawProductInquiryCommand(authorUserId = principal.userId, inquiryPublicId = inquiryPublicId),
        )
        return ResponseEntity.noContent().build()
    }
}
