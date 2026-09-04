package com.aechak.api.order.group

import com.aechak.api.order.group.request.CreateOrderGroupRequest
import com.aechak.api.order.group.response.CreateOrderGroupResponse
import com.aechak.application.order.usecase.OrderUseCase
import com.aechak.common.error.BusinessException
import com.aechak.domain.order.error.OrderErrorCode
import com.aechak.domain.order.group.OrderGroup
import com.aechak.webcommon.http.CustomHttpHeaders
import com.aechak.webcommon.response.ApiResponse
import com.aechak.websecurity.authentication.AuthPrincipal
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/order-groups")
class OrderGroupController(
    private val orderUseCase: OrderUseCase,
) {
    @PostMapping
    fun createOrderGroup(
        @RequestHeader(CustomHttpHeaders.IDEMPOTENCY_KEY) idempotencyKey: String,
        @Valid @RequestBody request: CreateOrderGroupRequest,
        @AuthenticationPrincipal principal: AuthPrincipal,
    ): ResponseEntity<ApiResponse<CreateOrderGroupResponse>> {
        validateIdempotencyKey(idempotencyKey)
        val result = orderUseCase.createOrderGroup(request.toCommand(principal.userId, idempotencyKey))
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.of(CreateOrderGroupResponse.from(result)))
    }

    /** 헤더는 본문 밖이라 Bean Validation이 못 닿음 — 컬럼 길이 초과가 DB까지 가기 전에 자름 */
    private fun validateIdempotencyKey(key: String) {
        if (key.isBlank() || key.length > OrderGroup.IDEMPOTENCY_KEY_MAX_LENGTH) {
            throw BusinessException(OrderErrorCode.INVALID_IDEMPOTENCY_KEY)
        }
    }
}
