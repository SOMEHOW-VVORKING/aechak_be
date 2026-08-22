package com.aechak.seller.product

import com.aechak.application.product.product.usecase.SellerProductUseCase
import com.aechak.seller.product.request.OptionCombinationChangeRequest
import com.aechak.seller.product.request.ProductRegisterRequest
import com.aechak.seller.product.request.ProductSaleStatusChangeRequest
import com.aechak.seller.product.request.ProductUpdateRequest
import com.aechak.seller.product.response.OptionCombinationChangeResponse
import com.aechak.seller.product.response.ProductRegisterResponse
import com.aechak.seller.product.response.ProductSaleStatusChangeResponse
import com.aechak.seller.product.response.ProductUpdateResponse
import com.aechak.webcommon.response.ApiResponse
import com.aechak.websecurity.authentication.AuthPrincipal
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
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
            .body(
                ApiResponse.of(ProductRegisterResponse.from(sellerProductUseCase.registerProduct(request.toCommand(principal.userId)))),
            )

    @PutMapping("/{productId}")
    fun update(
        @PathVariable productId: String,
        @Valid @RequestBody request: ProductUpdateRequest,
        @AuthenticationPrincipal principal: AuthPrincipal,
    ): ResponseEntity<ApiResponse<ProductUpdateResponse>> =
        ResponseEntity.ok(
            ApiResponse.of(
                ProductUpdateResponse.from(sellerProductUseCase.updateProduct(request.toCommand(principal.userId, productId))),
            ),
        )

    @PatchMapping("/{productId}/options/{combinationId}")
    fun changeOptionCombination(
        @PathVariable productId: String,
        @PathVariable combinationId: Long,
        @Valid @RequestBody request: OptionCombinationChangeRequest,
        @AuthenticationPrincipal principal: AuthPrincipal,
    ): ResponseEntity<ApiResponse<OptionCombinationChangeResponse>> =
        ResponseEntity.ok(
            ApiResponse.of(
                OptionCombinationChangeResponse.from(
                    sellerProductUseCase.changeOptionCombination(request.toCommand(principal.userId, productId, combinationId)),
                ),
            ),
        )

    @PatchMapping("/{productId}/status")
    fun changeSaleStatus(
        @PathVariable productId: String,
        @Valid @RequestBody request: ProductSaleStatusChangeRequest,
        @AuthenticationPrincipal principal: AuthPrincipal,
    ): ResponseEntity<ApiResponse<ProductSaleStatusChangeResponse>> =
        ResponseEntity.ok(
            ApiResponse.of(
                ProductSaleStatusChangeResponse.from(
                    sellerProductUseCase.changeProductSaleStatus(request.toCommand(principal.userId, productId)),
                ),
            ),
        )
}
