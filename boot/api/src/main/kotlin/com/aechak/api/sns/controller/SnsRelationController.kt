package com.aechak.api.sns.controller

import com.aechak.application.sns.usecase.SnsUseCase
import com.aechak.application.sns.usecase.command.BlockUserCommand
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
class SnsRelationController(
    private val snsUseCase: SnsUseCase,
) {
    @PostMapping("/users/{publicId}/block")
    @ResponseStatus(HttpStatus.CREATED)
    fun block(
        @PathVariable publicId: String,
        @AuthenticationPrincipal principal: AuthPrincipal,
    ): ResponseEntity<Void> {
        snsUseCase.block(BlockUserCommand(principal.userId, publicId))
        return ResponseEntity.status(HttpStatus.CREATED).build()
    }

    @DeleteMapping("/users/{publicId}/block")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun unblock(
        @PathVariable publicId: String,
        @AuthenticationPrincipal principal: AuthPrincipal,
    ): ResponseEntity<Void> {
        snsUseCase.unblock(BlockUserCommand(principal.userId, publicId))
        return ResponseEntity.noContent().build()
    }
}
