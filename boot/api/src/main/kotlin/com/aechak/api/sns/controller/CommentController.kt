package com.aechak.api.sns.controller

import com.aechak.api.sns.request.CreateCommentRequest
import com.aechak.api.sns.request.SnsCommentListRequest
import com.aechak.api.sns.request.UpdateCommentRequest
import com.aechak.api.sns.response.SnsCommentItemResponse
import com.aechak.api.sns.response.SnsCommentPageResponse
import com.aechak.application.sns.usecase.SnsCommentUseCase
import com.aechak.common.error.BusinessException
import com.aechak.common.error.CommonErrorCode
import com.aechak.domain.sns.comment.Comment
import com.aechak.webcommon.http.CustomHttpHeaders
import com.aechak.webcommon.response.ApiResponse
import com.aechak.websecurity.authentication.AuthPrincipal
import jakarta.validation.Valid
import org.springdoc.core.annotations.ParameterObject
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.ModelAttribute
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/sns")
class CommentController(
    private val snsCommentUseCase: SnsCommentUseCase,
) {
    @GetMapping("/posts/{publicId}/comments")
    fun getComments(
        @PathVariable publicId: String,
        @Valid @ModelAttribute @ParameterObject request: SnsCommentListRequest,
        @AuthenticationPrincipal principal: AuthPrincipal?,
    ): ResponseEntity<ApiResponse<SnsCommentPageResponse>> =
        ResponseEntity.ok(
            ApiResponse.of(SnsCommentPageResponse.from(snsCommentUseCase.getComments(request.toQuery(publicId, principal?.userId)))),
        )

    @PostMapping("/posts/{publicId}/comments")
    @ResponseStatus(HttpStatus.CREATED)
    fun createComment(
        @PathVariable publicId: String,
        @RequestHeader(CustomHttpHeaders.IDEMPOTENCY_KEY) idempotencyKey: String,
        @RequestBody request: CreateCommentRequest,
        @AuthenticationPrincipal principal: AuthPrincipal,
    ): ResponseEntity<ApiResponse<SnsCommentItemResponse>> {
        validateIdempotencyKey(idempotencyKey)
        val result = snsCommentUseCase.createComment(request.toCommand(principal.userId, publicId, idempotencyKey))
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.of(SnsCommentItemResponse.from(result)))
    }

    @PatchMapping("/comments/{commentPublicId}")
    fun updateComment(
        @PathVariable commentPublicId: String,
        @RequestBody request: UpdateCommentRequest,
        @AuthenticationPrincipal principal: AuthPrincipal,
    ): ResponseEntity<ApiResponse<SnsCommentItemResponse>> =
        ResponseEntity.ok(
            ApiResponse.of(
                SnsCommentItemResponse.from(snsCommentUseCase.updateComment(request.toCommand(principal.userId, commentPublicId))),
            ),
        )

    @DeleteMapping("/comments/{commentPublicId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun deleteComment(
        @PathVariable commentPublicId: String,
        @AuthenticationPrincipal principal: AuthPrincipal,
    ): ResponseEntity<Void> {
        snsCommentUseCase.deleteComment(principal.userId, commentPublicId)
        return ResponseEntity.noContent().build()
    }

    /** 헤더는 본문 밖이라 Bean Validation이 못 닿음. 컬럼 길이 초과가 DB까지 가기 전에 자름 */
    private fun validateIdempotencyKey(key: String) {
        if (key.isBlank() || key.length > Comment.IDEMPOTENCY_KEY_MAX_LENGTH) {
            throw BusinessException(CommonErrorCode.INVALID_IDEMPOTENCY_KEY)
        }
    }
}
