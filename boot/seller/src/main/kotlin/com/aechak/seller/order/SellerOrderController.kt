package com.aechak.seller.order

import com.aechak.application.order.seller.usecase.SellerOrderUseCase
import com.aechak.application.order.seller.usecase.command.AcceptOrderCommand
import com.aechak.application.order.seller.usecase.command.CompleteDeliveryCommand
import com.aechak.seller.order.request.SellerOrderSearchRequest
import com.aechak.seller.order.request.ShipmentRegisterRequest
import com.aechak.seller.order.response.SellerOrderDetailResponse
import com.aechak.seller.order.response.SellerOrderListResponse
import com.aechak.seller.order.response.ShipmentRegisterResponse
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
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/sellers/me/orders")
class SellerOrderController(
    private val sellerOrderUseCase: SellerOrderUseCase,
) {
    /** 발주 확인 — 결제완료 주문을 상품 준비중으로 전이한다. */
    @PostMapping("/{orderPublicId}/accept")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun accept(
        @PathVariable orderPublicId: String,
        @AuthenticationPrincipal principal: AuthPrincipal,
    ): ResponseEntity<Void> {
        sellerOrderUseCase.acceptOrder(
            AcceptOrderCommand(sellerId = principal.userId, orderPublicId = orderPublicId),
        )
        return ResponseEntity.noContent().build()
    }

    /** 배송완료 — 배송중 주문을 배송완료로 전이하고 구매확정 기한을 부여한다. */
    @PostMapping("/{orderPublicId}/delivery-complete")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun completeDelivery(
        @PathVariable orderPublicId: String,
        @AuthenticationPrincipal principal: AuthPrincipal,
    ): ResponseEntity<Void> {
        sellerOrderUseCase.completeDelivery(
            CompleteDeliveryCommand(sellerId = principal.userId, orderPublicId = orderPublicId),
        )
        return ResponseEntity.noContent().build()
    }

    /** 송장 등록 — 발송을 확정하고 주문을 배송중으로 전이한다. */
    @PostMapping("/{orderPublicId}/shipment")
    @ResponseStatus(HttpStatus.CREATED)
    fun registerShipment(
        @PathVariable orderPublicId: String,
        @Valid @RequestBody request: ShipmentRegisterRequest,
        @AuthenticationPrincipal principal: AuthPrincipal,
    ): ResponseEntity<ApiResponse<ShipmentRegisterResponse>> {
        val result = sellerOrderUseCase.registerShipment(request.toCommand(principal.userId, orderPublicId))
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.of(ShipmentRegisterResponse.from(result)))
    }

    /** 내 주문 목록 — 상태 세그먼트·키워드·주문일 범위 필터와 오프셋 페이지네이션 */
    @GetMapping
    fun getMyOrders(
        @Valid @ModelAttribute request: SellerOrderSearchRequest,
        @AuthenticationPrincipal principal: AuthPrincipal,
    ): ResponseEntity<ApiResponse<SellerOrderListResponse>> =
        ResponseEntity.ok(
            ApiResponse.of(SellerOrderListResponse.from(sellerOrderUseCase.getMyOrders(request.toQuery(principal.userId)))),
        )

    /** 내 주문 상세 — 품목·배송·클레임 포함 */
    @GetMapping("/{orderPublicId}")
    fun getMyOrderDetail(
        @PathVariable orderPublicId: String,
        @AuthenticationPrincipal principal: AuthPrincipal,
    ): ResponseEntity<ApiResponse<SellerOrderDetailResponse>> =
        ResponseEntity.ok(
            ApiResponse.of(SellerOrderDetailResponse.from(sellerOrderUseCase.getMyOrderDetail(principal.userId, orderPublicId))),
        )
}
