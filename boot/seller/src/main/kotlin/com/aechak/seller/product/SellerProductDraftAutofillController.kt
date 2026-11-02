package com.aechak.seller.product

import com.aechak.application.product.draft.usecase.ProductDraftAutofillUseCase
import com.aechak.seller.product.request.ProductDraftAutofillRequest
import com.aechak.seller.product.response.ProductDraftAutofillResponse
import com.aechak.webcommon.response.ApiResponse
import com.aechak.websecurity.authentication.AuthPrincipal
import jakarta.validation.Valid
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/**
 * 폼 초안 받기. 임시저장 CRUD(SellerProductDraftController)와 수명주기가 달라 별 컨트롤러다 —
 * 이쪽은 아무것도 저장하지 않고 페이로드 코덱도 쓰지 않는다.
 */
@RestController
@RequestMapping("/sellers/me/product-drafts")
class SellerProductDraftAutofillController(
    private val autofillUseCase: ProductDraftAutofillUseCase,
) {
    /** AI 가 못 만들어도 200 + state 로 돌려준다 — 초안 실패가 셀러의 등록을 막지 않는다 */
    @PostMapping("/autofill")
    fun autofill(
        @Valid @RequestBody request: ProductDraftAutofillRequest,
        @AuthenticationPrincipal principal: AuthPrincipal,
    ): ResponseEntity<ApiResponse<ProductDraftAutofillResponse>> =
        ResponseEntity.ok(
            ApiResponse.of(ProductDraftAutofillResponse.from(autofillUseCase.autofill(request.toCommand(principal.userId)))),
        )
}
