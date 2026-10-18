package com.aechak.seller.order

import com.aechak.application.order.seller.usecase.SellerOrderUseCase
import com.aechak.application.order.seller.usecase.command.AcceptOrderCommand
import com.aechak.websecurity.authentication.AuthPrincipal
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/sellers/me/orders")
class SellerOrderController(
    private val sellerOrderUseCase: SellerOrderUseCase,
) {
    /** 발주 확인 — 결제완료 주문을 상품 준비중으로 전이한다. */
    @PostMapping("/{orderPublicId}/accept")
    fun accept(
        @PathVariable orderPublicId: String,
        @AuthenticationPrincipal principal: AuthPrincipal,
    ): ResponseEntity<Void> {
        sellerOrderUseCase.acceptOrder(
            AcceptOrderCommand(sellerId = principal.userId, orderPublicId = orderPublicId),
        )
        return ResponseEntity.noContent().build()
    }
}
