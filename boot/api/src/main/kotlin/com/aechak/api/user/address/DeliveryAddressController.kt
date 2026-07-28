package com.aechak.api.user.address

import com.aechak.api.user.address.request.AddDeliveryAddressRequest
import com.aechak.api.user.address.request.UpdateDeliveryAddressRequest
import com.aechak.api.user.address.response.AddDeliveryAddressResponse
import com.aechak.api.user.address.response.DeleteDeliveryAddressResponse
import com.aechak.api.user.address.response.DeliveryAddressListResponse
import com.aechak.api.user.address.response.SetDefaultDeliveryAddressResponse
import com.aechak.api.user.address.response.UpdateDeliveryAddressResponse
import com.aechak.application.user.address.usecase.DeliveryAddressUseCase
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
@RequestMapping("/delivery-addresses") // 접두(api.base-path)는 WebConfig가 일괄 부착
class DeliveryAddressController(
    private val deliveryAddressUseCase: DeliveryAddressUseCase,
) {
    @GetMapping
    fun getDeliveryAddresses(
        @AuthenticationPrincipal principal: AuthPrincipal,
    ): ResponseEntity<ApiResponse<DeliveryAddressListResponse>> =
        ResponseEntity.ok(ApiResponse.of(DeliveryAddressListResponse.from(deliveryAddressUseCase.getDeliveryAddresses(principal.userId))))

    @PostMapping
    fun addDeliveryAddress(
        @Valid @RequestBody request: AddDeliveryAddressRequest,
        @AuthenticationPrincipal principal: AuthPrincipal,
    ): ResponseEntity<ApiResponse<AddDeliveryAddressResponse>> {
        val result = deliveryAddressUseCase.addDeliveryAddress(request.toCommand(principal.userId))
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.of(AddDeliveryAddressResponse.from(result)))
    }

    @PatchMapping("/{addressId}")
    fun updateDeliveryAddress(
        @PathVariable addressId: Long,
        @Valid @RequestBody request: UpdateDeliveryAddressRequest,
        @AuthenticationPrincipal principal: AuthPrincipal,
    ): ResponseEntity<ApiResponse<UpdateDeliveryAddressResponse>> {
        val result = deliveryAddressUseCase.updateDeliveryAddress(request.toCommand(principal.userId, addressId))
        return ResponseEntity.ok(ApiResponse.of(UpdateDeliveryAddressResponse.from(result)))
    }

    @DeleteMapping("/{addressId}")
    fun deleteDeliveryAddress(
        @PathVariable addressId: Long,
        @AuthenticationPrincipal principal: AuthPrincipal,
    ): ResponseEntity<ApiResponse<DeleteDeliveryAddressResponse>> {
        val result = deliveryAddressUseCase.deleteDeliveryAddress(principal.userId, addressId)
        return ResponseEntity.ok(ApiResponse.of(DeleteDeliveryAddressResponse.from(result)))
    }

    @PatchMapping("/{addressId}/default")
    fun setDefaultDeliveryAddress(
        @PathVariable addressId: Long,
        @AuthenticationPrincipal principal: AuthPrincipal,
    ): ResponseEntity<ApiResponse<SetDefaultDeliveryAddressResponse>> {
        val result = deliveryAddressUseCase.setDefaultDeliveryAddress(principal.userId, addressId)
        return ResponseEntity.ok(ApiResponse.of(SetDefaultDeliveryAddressResponse.from(result)))
    }
}
