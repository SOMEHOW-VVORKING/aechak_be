package com.aechak.api.sns.controller

import com.aechak.api.sns.request.SnsFeedRequest
import com.aechak.api.sns.response.SnsPostGridPageResponse
import com.aechak.application.sns.usecase.SnsFeedUseCase
import com.aechak.webcommon.response.ApiResponse
import com.aechak.websecurity.authentication.AuthPrincipal
import jakarta.validation.Valid
import org.springdoc.core.annotations.ParameterObject
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.ModelAttribute
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/sns")
class SnsPostTabController(
    private val snsFeedUseCase: SnsFeedUseCase,
) {
    @GetMapping("/users/{publicId}/posts")
    fun userPosts(
        @PathVariable publicId: String,
        @Valid @ModelAttribute @ParameterObject request: SnsFeedRequest,
        @AuthenticationPrincipal principal: AuthPrincipal?,
    ): ResponseEntity<ApiResponse<SnsPostGridPageResponse>> =
        ResponseEntity.ok(
            ApiResponse.of(SnsPostGridPageResponse.from(snsFeedUseCase.userPosts(request.toTabQuery(principal?.userId, publicId)))),
        )

    @GetMapping("/users/{publicId}/videos")
    fun userVideos(
        @PathVariable publicId: String,
        @Valid @ModelAttribute @ParameterObject request: SnsFeedRequest,
        @AuthenticationPrincipal principal: AuthPrincipal?,
    ): ResponseEntity<ApiResponse<SnsPostGridPageResponse>> =
        ResponseEntity.ok(
            ApiResponse.of(SnsPostGridPageResponse.from(snsFeedUseCase.userVideos(request.toTabQuery(principal?.userId, publicId)))),
        )

    @GetMapping("/users/{publicId}/reposts")
    fun userReposts(
        @PathVariable publicId: String,
        @Valid @ModelAttribute @ParameterObject request: SnsFeedRequest,
        @AuthenticationPrincipal principal: AuthPrincipal?,
    ): ResponseEntity<ApiResponse<SnsPostGridPageResponse>> =
        ResponseEntity.ok(
            ApiResponse.of(SnsPostGridPageResponse.from(snsFeedUseCase.userReposts(request.toTabQuery(principal?.userId, publicId)))),
        )

    @GetMapping("/me/saves")
    fun mySaves(
        @Valid @ModelAttribute @ParameterObject request: SnsFeedRequest,
        @AuthenticationPrincipal principal: AuthPrincipal,
    ): ResponseEntity<ApiResponse<SnsPostGridPageResponse>> =
        ResponseEntity.ok(ApiResponse.of(SnsPostGridPageResponse.from(snsFeedUseCase.mySaves(request.toQuery(principal.userId)))))
}
