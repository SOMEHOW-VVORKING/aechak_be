package com.aechak.seller.product

import com.aechak.application.product.usecase.SellerProductUseCase
import com.aechak.seller.product.request.ProductRegisterRequest
import com.aechak.seller.product.request.SellerProductSearchRequest
import com.aechak.seller.product.response.ProductRegisterResponse
import com.aechak.seller.product.response.SellerProductListResponse
import com.aechak.seller.product.response.SellerProductOptionsResponse
import com.aechak.webcommon.response.ApiResponse
import com.aechak.websecurity.authentication.AuthPrincipal
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.ModelAttribute
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/sellers/me/products")
class SellerProductController(
    private val sellerProductUseCase: SellerProductUseCase,
) {
    @PostMapping
    fun register(
        @Valid @RequestBody request: ProductRegisterRequest,
        @AuthenticationPrincipal principal: AuthPrincipal,
    ): ResponseEntity<ApiResponse<ProductRegisterResponse>> =
        ResponseEntity
            .status(HttpStatus.CREATED)
            .body(ApiResponse.of(ProductRegisterResponse.from(sellerProductUseCase.registerProduct(request.toCommand(principal.userId)))))

    /** 내 상품 목록 — 필터·정렬·오프셋 페이지네이션 */
    @GetMapping
    fun getMyProducts(
        @Valid @ModelAttribute request: SellerProductSearchRequest,
        @AuthenticationPrincipal principal: AuthPrincipal,
    ): ResponseEntity<ApiResponse<SellerProductListResponse>> =
        ResponseEntity.ok(
            ApiResponse.of(SellerProductListResponse.from(sellerProductUseCase.getMyProducts(request.toQuery(principal.userId)))),
        )

    /** 내 상품의 옵션 조합별 재고 — 목록 행 클릭 시 모달용 */
    @GetMapping("/{productId}/options")
    fun getMyProductOptions(
        @PathVariable productId: String,
        @AuthenticationPrincipal principal: AuthPrincipal,
    ): ResponseEntity<ApiResponse<SellerProductOptionsResponse>> =
        ResponseEntity.ok(
            ApiResponse.of(SellerProductOptionsResponse.from(sellerProductUseCase.getMyProductOptions(principal.userId, productId))),
        )
}
