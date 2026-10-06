package com.aechak.api.product.home

import com.aechak.api.product.home.response.HomeResponse
import com.aechak.application.product.product.usecase.ProductUseCase
import com.aechak.webcommon.response.ApiResponse
import com.aechak.websecurity.authentication.AuthPrincipal
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/home")
class HomeController(
    private val productUseCase: ProductUseCase,
) {
    @GetMapping
    fun getHome(
        @AuthenticationPrincipal principal: AuthPrincipal?,
    ): ResponseEntity<ApiResponse<HomeResponse>> =
        ResponseEntity.ok(ApiResponse.of(HomeResponse.from(productUseCase.getCuration(principal?.userId))))
}
