package com.aechak.api.sns.controller

import com.aechak.api.sns.request.SnsHashtagPostsRequest
import com.aechak.api.sns.request.SnsSearchRequest
import com.aechak.api.sns.response.SnsHashtagPostPageResponse
import com.aechak.api.sns.response.SnsSearchHashtagPageResponse
import com.aechak.api.sns.response.SnsSearchPostPageResponse
import com.aechak.api.sns.response.SnsSearchUserPageResponse
import com.aechak.application.sns.usecase.SnsSearchUseCase
import com.aechak.webcommon.response.ApiResponse
import com.aechak.websecurity.authentication.AuthPrincipal
import jakarta.validation.Valid
import org.springdoc.core.annotations.ParameterObject
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.ModelAttribute
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RestController

@RestController
class SnsSearchController(
    private val snsSearchUseCase: SnsSearchUseCase,
) {
    @GetMapping("/sns/search/posts")
    fun searchPosts(
        @Valid @ModelAttribute @ParameterObject request: SnsSearchRequest,
        @AuthenticationPrincipal principal: AuthPrincipal?,
    ): ResponseEntity<ApiResponse<SnsSearchPostPageResponse>> =
        ResponseEntity.ok(ApiResponse.of(SnsSearchPostPageResponse.from(snsSearchUseCase.searchPosts(request.toQuery(principal?.userId)))))

    @GetMapping("/sns/search/users")
    fun searchUsers(
        @Valid @ModelAttribute @ParameterObject request: SnsSearchRequest,
        @AuthenticationPrincipal principal: AuthPrincipal?,
    ): ResponseEntity<ApiResponse<SnsSearchUserPageResponse>> =
        ResponseEntity.ok(ApiResponse.of(SnsSearchUserPageResponse.from(snsSearchUseCase.searchUsers(request.toQuery(principal?.userId)))))

    @GetMapping("/sns/search/hashtags")
    fun searchHashtags(
        @Valid @ModelAttribute @ParameterObject request: SnsSearchRequest,
        @AuthenticationPrincipal principal: AuthPrincipal?,
    ): ResponseEntity<ApiResponse<SnsSearchHashtagPageResponse>> =
        ResponseEntity.ok(
            ApiResponse.of(SnsSearchHashtagPageResponse.from(snsSearchUseCase.searchHashtags(request.toQuery(principal?.userId)))),
        )

    @GetMapping("/sns/hashtags/{name}/posts")
    fun hashtagPosts(
        @PathVariable name: String,
        @Valid @ModelAttribute @ParameterObject request: SnsHashtagPostsRequest,
        @AuthenticationPrincipal principal: AuthPrincipal?,
    ): ResponseEntity<ApiResponse<SnsHashtagPostPageResponse>> =
        ResponseEntity.ok(
            ApiResponse.of(SnsHashtagPostPageResponse.from(snsSearchUseCase.hashtagPosts(request.toQuery(principal?.userId, name)))),
        )
}
