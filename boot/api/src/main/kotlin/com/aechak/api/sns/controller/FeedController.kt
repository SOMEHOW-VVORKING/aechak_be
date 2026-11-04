package com.aechak.api.sns.controller

import com.aechak.api.sns.request.SnsFeedRequest
import com.aechak.api.sns.response.SnsFeedPageResponse
import com.aechak.application.sns.usecase.SnsFeedUseCase
import com.aechak.webcommon.response.ApiResponse
import com.aechak.websecurity.authentication.AuthPrincipal
import jakarta.validation.Valid
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.ModelAttribute
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/sns/feeds")
class FeedController(
    private val snsFeedUseCase: SnsFeedUseCase,
) {
    @GetMapping("/explore")
    fun explore(
        @Valid @ModelAttribute request: SnsFeedRequest,
        @AuthenticationPrincipal principal: AuthPrincipal?,
    ): ResponseEntity<ApiResponse<SnsFeedPageResponse>> =
        ResponseEntity.ok(ApiResponse.of(SnsFeedPageResponse.from(snsFeedUseCase.explore(request.toQuery(principal?.userId)))))
}
