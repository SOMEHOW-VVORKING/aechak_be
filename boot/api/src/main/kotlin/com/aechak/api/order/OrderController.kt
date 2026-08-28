package com.aechak.api.order

import com.aechak.api.order.request.OrderListRequest
import com.aechak.api.order.response.OrderDetailResponse
import com.aechak.api.order.response.OrderListResponse
import com.aechak.application.order.usecase.OrderQueryUseCase
import com.aechak.webcommon.response.ApiResponse
import com.aechak.websecurity.authentication.AuthPrincipal
import jakarta.validation.Valid
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.ModelAttribute
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RestController

@RestController
class OrderController(
    private val orderQueryUseCase: OrderQueryUseCase,
) {
    @GetMapping("/order-groups")
    fun getOrderGroups(
        @Valid @ModelAttribute request: OrderListRequest,
        @AuthenticationPrincipal principal: AuthPrincipal,
    ): ResponseEntity<ApiResponse<OrderListResponse>> =
        ResponseEntity.ok(
            ApiResponse.of(OrderListResponse.from(orderQueryUseCase.getOrders(request.toQuery(principal.userId)))),
        )

    @GetMapping("/orders/{orderId}")
    fun getOrderDetail(
        @PathVariable orderId: String,
        @AuthenticationPrincipal principal: AuthPrincipal,
    ): ResponseEntity<ApiResponse<OrderDetailResponse>> =
        ResponseEntity.ok(
            ApiResponse.of(OrderDetailResponse.from(orderQueryUseCase.getOrderDetail(orderId, principal.userId))),
        )
}
