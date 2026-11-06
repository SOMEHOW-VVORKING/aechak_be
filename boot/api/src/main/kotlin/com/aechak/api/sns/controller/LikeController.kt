package com.aechak.api.sns.controller

import com.aechak.api.sns.response.SnsLikeResponse
import com.aechak.api.sns.response.SnsLikerListResponse
import com.aechak.application.sns.usecase.SnsLikeUseCase
import com.aechak.application.sns.usecase.command.LikeCommentCommand
import com.aechak.application.sns.usecase.command.LikePostCommand
import com.aechak.webcommon.response.ApiResponse
import com.aechak.websecurity.authentication.AuthPrincipal
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/sns")
class LikeController(
    private val snsLikeUseCase: SnsLikeUseCase,
) {
    @PostMapping("/posts/{publicId}/like")
    @ResponseStatus(HttpStatus.CREATED)
    fun likePost(
        @PathVariable publicId: String,
        @AuthenticationPrincipal principal: AuthPrincipal,
    ): ResponseEntity<ApiResponse<SnsLikeResponse>> {
        val result = snsLikeUseCase.likePost(LikePostCommand(principal.userId, publicId))
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.of(SnsLikeResponse.from(result)))
    }

    @DeleteMapping("/posts/{publicId}/like")
    fun unlikePost(
        @PathVariable publicId: String,
        @AuthenticationPrincipal principal: AuthPrincipal,
    ): ResponseEntity<ApiResponse<SnsLikeResponse>> =
        ResponseEntity.ok(ApiResponse.of(SnsLikeResponse.from(snsLikeUseCase.unlikePost(LikePostCommand(principal.userId, publicId)))))

    @GetMapping("/posts/{publicId}/likers")
    fun getPostLikers(
        @PathVariable publicId: String,
        @AuthenticationPrincipal principal: AuthPrincipal,
    ): ResponseEntity<ApiResponse<SnsLikerListResponse>> =
        ResponseEntity.ok(
            ApiResponse.of(SnsLikerListResponse.from(snsLikeUseCase.getPostLikers(LikePostCommand(principal.userId, publicId)))),
        )

    @PostMapping("/comments/{commentPublicId}/like")
    @ResponseStatus(HttpStatus.CREATED)
    fun likeComment(
        @PathVariable commentPublicId: String,
        @AuthenticationPrincipal principal: AuthPrincipal,
    ): ResponseEntity<ApiResponse<SnsLikeResponse>> {
        val result = snsLikeUseCase.likeComment(LikeCommentCommand(principal.userId, commentPublicId))
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.of(SnsLikeResponse.from(result)))
    }

    @DeleteMapping("/comments/{commentPublicId}/like")
    fun unlikeComment(
        @PathVariable commentPublicId: String,
        @AuthenticationPrincipal principal: AuthPrincipal,
    ): ResponseEntity<ApiResponse<SnsLikeResponse>> =
        ResponseEntity.ok(
            ApiResponse.of(SnsLikeResponse.from(snsLikeUseCase.unlikeComment(LikeCommentCommand(principal.userId, commentPublicId)))),
        )

    @GetMapping("/comments/{commentPublicId}/likers")
    fun getCommentLikers(
        @PathVariable commentPublicId: String,
        @AuthenticationPrincipal principal: AuthPrincipal,
    ): ResponseEntity<ApiResponse<SnsLikerListResponse>> =
        ResponseEntity.ok(
            ApiResponse.of(
                SnsLikerListResponse.from(snsLikeUseCase.getCommentLikers(LikeCommentCommand(principal.userId, commentPublicId))),
            ),
        )
}
