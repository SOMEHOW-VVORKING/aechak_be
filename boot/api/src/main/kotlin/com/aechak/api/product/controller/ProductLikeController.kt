package com.aechak.api.product.controller

import com.aechak.application.product.like.usecase.ProductLikeUseCase
import com.aechak.application.product.like.usecase.command.ProductLikeCommand
import com.aechak.websecurity.authentication.AuthPrincipal
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/products")
class ProductLikeController(
    private val productLikeUseCase: ProductLikeUseCase,
) {
    @PostMapping("/{productId}/like")
    fun likeProduct(
        @PathVariable productId: String,
        @AuthenticationPrincipal principal: AuthPrincipal,
    ): ResponseEntity<Void> {
        productLikeUseCase.like(ProductLikeCommand(productId, principal.userId))
        return ResponseEntity.status(HttpStatus.CREATED).build()
    }

    @DeleteMapping("/{productId}/like")
    fun unlikeProduct(
        @PathVariable productId: String,
        @AuthenticationPrincipal principal: AuthPrincipal,
    ): ResponseEntity<Void> {
        productLikeUseCase.unlike(ProductLikeCommand(productId, principal.userId))
        return ResponseEntity.noContent().build()
    }
}
