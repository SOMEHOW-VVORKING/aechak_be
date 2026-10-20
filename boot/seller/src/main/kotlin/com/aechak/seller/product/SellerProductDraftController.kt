package com.aechak.seller.product

import com.aechak.application.product.draft.usecase.ProductDraftUseCase
import com.aechak.seller.product.request.ProductDraftSaveRequest
import com.aechak.seller.product.response.ProductDraftListResponse
import com.aechak.seller.product.response.ProductDraftResponse
import com.aechak.seller.product.response.ProductDraftSaveResponse
import com.aechak.seller.product.support.ProductDraftPayloadCodec
import com.aechak.webcommon.response.ApiResponse
import com.aechak.websecurity.authentication.AuthPrincipal
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/sellers/me/product-drafts")
class SellerProductDraftController(
    private val productDraftUseCase: ProductDraftUseCase,
    private val payloadCodec: ProductDraftPayloadCodec,
) {
    /** 초안 목록 — 임시저장 버튼의 개수 배지와 이어쓰기 Drawer용 */
    @GetMapping
    fun getMyDrafts(
        @AuthenticationPrincipal principal: AuthPrincipal,
    ): ResponseEntity<ApiResponse<ProductDraftListResponse>> =
        ResponseEntity.ok(ApiResponse.of(ProductDraftListResponse.from(productDraftUseCase.getMyDrafts(principal.userId))))

    /** 초안 단건 — 등록 폼 프리필용 */
    @GetMapping("/{draftId}")
    fun getMyDraft(
        @PathVariable draftId: String,
        @AuthenticationPrincipal principal: AuthPrincipal,
    ): ResponseEntity<ApiResponse<ProductDraftResponse>> {
        val result = productDraftUseCase.getMyDraft(principal.userId, draftId)
        return ResponseEntity.ok(ApiResponse.of(ProductDraftResponse.from(result, payloadCodec.decode(result.payload))))
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    fun create(
        @Valid @RequestBody request: ProductDraftSaveRequest,
        @AuthenticationPrincipal principal: AuthPrincipal,
    ): ResponseEntity<ApiResponse<ProductDraftSaveResponse>> {
        val command = request.toCommand(principal.userId, null, payloadCodec.encode(request.toPayload()))
        return ResponseEntity
            .status(HttpStatus.CREATED)
            .body(ApiResponse.of(ProductDraftSaveResponse.from(productDraftUseCase.saveMyDraft(command))))
    }

    @PutMapping("/{draftId}")
    fun update(
        @PathVariable draftId: String,
        @Valid @RequestBody request: ProductDraftSaveRequest,
        @AuthenticationPrincipal principal: AuthPrincipal,
    ): ResponseEntity<ApiResponse<ProductDraftSaveResponse>> {
        val command = request.toCommand(principal.userId, draftId, payloadCodec.encode(request.toPayload()))
        return ResponseEntity.ok(ApiResponse.of(ProductDraftSaveResponse.from(productDraftUseCase.saveMyDraft(command))))
    }

    @DeleteMapping("/{draftId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun delete(
        @PathVariable draftId: String,
        @AuthenticationPrincipal principal: AuthPrincipal,
    ): ResponseEntity<Void> {
        productDraftUseCase.deleteMyDraft(principal.userId, draftId)
        return ResponseEntity.noContent().build()
    }
}
