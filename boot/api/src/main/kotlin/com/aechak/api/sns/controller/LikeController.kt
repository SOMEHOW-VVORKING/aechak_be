package com.aechak.api.sns.controller

import com.aechak.api.sns.response.SnsLikeResponse
import com.aechak.application.sns.usecase.SnsLikeUseCase
import com.aechak.application.sns.usecase.command.LikePostCommand
import com.aechak.webcommon.response.ApiResponse
import com.aechak.websecurity.authentication.AuthPrincipal
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/sns/posts")
class LikeController(
    private val snsLikeUseCase: SnsLikeUseCase,
) {
    @PostMapping("/{publicId}/like")
    @ResponseStatus(HttpStatus.CREATED)
    fun likePost(
        @PathVariable publicId: String,
        @AuthenticationPrincipal principal: AuthPrincipal,
    ): ResponseEntity<ApiResponse<SnsLikeResponse>> {
        val result = snsLikeUseCase.likePost(LikePostCommand(principal.userId, publicId))
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.of(SnsLikeResponse.from(result)))
    }

    @DeleteMapping("/{publicId}/like")
    fun unlikePost(
        @PathVariable publicId: String,
        @AuthenticationPrincipal principal: AuthPrincipal,
    ): ResponseEntity<ApiResponse<SnsLikeResponse>> =
        ResponseEntity.ok(ApiResponse.of(SnsLikeResponse.from(snsLikeUseCase.unlikePost(LikePostCommand(principal.userId, publicId)))))
}
