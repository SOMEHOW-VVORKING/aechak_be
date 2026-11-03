package com.aechak.api.sns.controller

import com.aechak.api.sns.request.CreatePostRequest
import com.aechak.api.sns.response.CreatePostResponse
import com.aechak.api.sns.response.PostDetailResponse
import com.aechak.application.sns.usecase.SnsPostUseCase
import com.aechak.common.error.BusinessException
import com.aechak.common.error.CommonErrorCode
import com.aechak.domain.sns.post.Post
import com.aechak.webcommon.http.CustomHttpHeaders
import com.aechak.webcommon.response.ApiResponse
import com.aechak.websecurity.authentication.AuthPrincipal
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/sns/posts")
class PostController(
    private val snsPostUseCase: SnsPostUseCase,
) {
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    fun createPost(
        @RequestHeader(CustomHttpHeaders.IDEMPOTENCY_KEY) idempotencyKey: String,
        @RequestBody request: CreatePostRequest,
        @AuthenticationPrincipal principal: AuthPrincipal,
    ): ResponseEntity<ApiResponse<CreatePostResponse>> {
        validateIdempotencyKey(idempotencyKey)
        val result = snsPostUseCase.createPost(request.toCommand(principal.userId, idempotencyKey))
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.of(CreatePostResponse.from(result)))
    }

    @GetMapping("/{publicId}")
    fun getPost(
        @PathVariable publicId: String,
        @AuthenticationPrincipal principal: AuthPrincipal?,
    ): ResponseEntity<ApiResponse<PostDetailResponse>> =
        ResponseEntity.ok(ApiResponse.of(PostDetailResponse.from(snsPostUseCase.getPost(publicId, principal?.userId))))

    /** 헤더는 본문 밖이라 Bean Validation이 못 닿음 — 컬럼 길이 초과가 DB까지 가기 전에 자름 */
    private fun validateIdempotencyKey(key: String) {
        if (key.isBlank() || key.length > Post.IDEMPOTENCY_KEY_MAX_LENGTH) {
            throw BusinessException(CommonErrorCode.INVALID_IDEMPOTENCY_KEY)
        }
    }
}
