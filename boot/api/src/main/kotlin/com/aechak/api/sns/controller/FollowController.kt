package com.aechak.api.sns.controller

import com.aechak.application.sns.usecase.SnsUseCase
import com.aechak.application.sns.usecase.command.FollowUserCommand
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
@RequestMapping("/sns")
class FollowController(
    private val snsUseCase: SnsUseCase,
) {
    @PostMapping("/users/{publicId}/follow")
    @ResponseStatus(HttpStatus.CREATED)
    fun follow(
        @PathVariable publicId: String,
        @AuthenticationPrincipal principal: AuthPrincipal,
    ): ResponseEntity<Void> {
        snsUseCase.follow(FollowUserCommand(principal.userId, publicId))
        return ResponseEntity.status(HttpStatus.CREATED).build()
    }

    @DeleteMapping("/users/{publicId}/follow")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun unfollow(
        @PathVariable publicId: String,
        @AuthenticationPrincipal principal: AuthPrincipal,
    ): ResponseEntity<Void> {
        snsUseCase.unfollow(FollowUserCommand(principal.userId, publicId))
        return ResponseEntity.noContent().build()
    }
}
