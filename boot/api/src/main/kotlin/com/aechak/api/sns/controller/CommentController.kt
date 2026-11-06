package com.aechak.api.sns.controller

import com.aechak.api.sns.request.SnsCommentListRequest
import com.aechak.api.sns.response.SnsCommentPageResponse
import com.aechak.application.sns.usecase.SnsCommentUseCase
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
}
