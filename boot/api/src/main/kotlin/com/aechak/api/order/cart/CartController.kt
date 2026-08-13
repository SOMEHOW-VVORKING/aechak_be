package com.aechak.api.order.cart

import com.aechak.api.order.cart.request.AddCartItemRequest
import com.aechak.api.order.cart.request.DeleteCartItemsRequest
import com.aechak.api.order.cart.request.UpdateCartItemRequest
import com.aechak.api.order.cart.response.AddCartItemResponse
import com.aechak.api.order.cart.response.CartItemCountResponse
import com.aechak.api.order.cart.response.CartResponse
import com.aechak.api.order.cart.response.DeleteCartItemsResponse
import com.aechak.api.order.cart.response.UpdateCartItemResponse
import com.aechak.application.order.cart.usecase.CartUseCase
import com.aechak.webcommon.response.ApiResponse
import com.aechak.websecurity.authentication.AuthPrincipal
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/carts")
class CartController(
    private val cartUseCase: CartUseCase,
) {
    @PostMapping("/items")
    fun addCartItem(
        @Valid @RequestBody request: AddCartItemRequest,
        @AuthenticationPrincipal principal: AuthPrincipal,
    ): ResponseEntity<ApiResponse<AddCartItemResponse>> {
        val result = cartUseCase.addCartItem(request.toCommand(principal.userId))
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.of(AddCartItemResponse.from(result)))
    }

    @GetMapping
    fun getCart(
        @AuthenticationPrincipal principal: AuthPrincipal,
    ): ResponseEntity<ApiResponse<CartResponse>> =
        ResponseEntity.ok(ApiResponse.of(CartResponse.from(cartUseCase.getCart(principal.userId))))

    @GetMapping("/items/count")
    fun countCartItems(
        @AuthenticationPrincipal principal: AuthPrincipal,
    ): ResponseEntity<ApiResponse<CartItemCountResponse>> =
        ResponseEntity.ok(ApiResponse.of(CartItemCountResponse.from(cartUseCase.countCartItems(principal.userId))))

    @PatchMapping("/items/{cartItemId}")
    fun updateCartItem(
        @PathVariable cartItemId: Long,
        @Valid @RequestBody request: UpdateCartItemRequest,
        @AuthenticationPrincipal principal: AuthPrincipal,
    ): ResponseEntity<ApiResponse<UpdateCartItemResponse>> {
        val result = cartUseCase.updateCartItem(request.toCommand(principal.userId, cartItemId))
        return ResponseEntity.ok(ApiResponse.of(UpdateCartItemResponse.from(result)))
    }

    @DeleteMapping("/items")
    fun deleteCartItems(
        @Valid @RequestBody request: DeleteCartItemsRequest,
        @AuthenticationPrincipal principal: AuthPrincipal,
    ): ResponseEntity<ApiResponse<DeleteCartItemsResponse>> {
        val result = cartUseCase.deleteCartItems(request.toCommand(principal.userId))
        return ResponseEntity.ok(ApiResponse.of(DeleteCartItemsResponse.from(result)))
    }
}
