package com.aechak.api.sns.controller

import com.aechak.application.sns.usecase.SnsPostUseCase
import com.aechak.application.sns.usecase.command.RepostPostCommand
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
class PostReactionController(
    private val snsPostUseCase: SnsPostUseCase,
) {
    @PostMapping("/{publicId}/repost")
    @ResponseStatus(HttpStatus.CREATED)
    fun repost(
        @PathVariable publicId: String,
        @AuthenticationPrincipal principal: AuthPrincipal,
    ): ResponseEntity<Void> {
        snsPostUseCase.repost(RepostPostCommand(principal.userId, publicId))
        return ResponseEntity.status(HttpStatus.CREATED).build()
    }

    @DeleteMapping("/{publicId}/repost")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun unrepost(
        @PathVariable publicId: String,
        @AuthenticationPrincipal principal: AuthPrincipal,
    ): ResponseEntity<Void> {
        snsPostUseCase.unrepost(RepostPostCommand(principal.userId, publicId))
        return ResponseEntity.noContent().build()
    }
}
