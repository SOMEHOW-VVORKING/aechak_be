package com.aechak.api.sns.controller

import com.aechak.api.sns.request.SnsRelationListRequest
import com.aechak.api.sns.response.SnsRelationPageResponse
import com.aechak.application.sns.port.SnsRelationKind
import com.aechak.application.sns.usecase.SnsUseCase
import com.aechak.application.sns.usecase.command.BlockUserCommand
import com.aechak.application.sns.usecase.command.MuteUserCommand
import com.aechak.webcommon.response.ApiResponse
import com.aechak.websecurity.authentication.AuthPrincipal
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.ModelAttribute
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

    @PostMapping("/users/{publicId}/mute")
    @ResponseStatus(HttpStatus.CREATED)
    fun mute(
        @PathVariable publicId: String,
        @AuthenticationPrincipal principal: AuthPrincipal,
    ): ResponseEntity<Void> {
        snsUseCase.mute(MuteUserCommand(principal.userId, publicId))
        return ResponseEntity.status(HttpStatus.CREATED).build()
    }

    @DeleteMapping("/users/{publicId}/mute")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun unmute(
        @PathVariable publicId: String,
        @AuthenticationPrincipal principal: AuthPrincipal,
    ): ResponseEntity<Void> {
        snsUseCase.unmute(MuteUserCommand(principal.userId, publicId))
        return ResponseEntity.noContent().build()
    }

    @GetMapping("/me/blocks")
    fun getMyBlocks(
        @Valid @ModelAttribute request: SnsRelationListRequest,
        @AuthenticationPrincipal principal: AuthPrincipal,
    ): ResponseEntity<ApiResponse<SnsRelationPageResponse>> = getMyRelations(request, principal, SnsRelationKind.BLOCK)

    @GetMapping("/me/mutes")
    fun getMyMutes(
        @Valid @ModelAttribute request: SnsRelationListRequest,
        @AuthenticationPrincipal principal: AuthPrincipal,
    ): ResponseEntity<ApiResponse<SnsRelationPageResponse>> = getMyRelations(request, principal, SnsRelationKind.MUTE)

    private fun getMyRelations(
        request: SnsRelationListRequest,
        principal: AuthPrincipal,
        kind: SnsRelationKind,
    ): ResponseEntity<ApiResponse<SnsRelationPageResponse>> =
        ResponseEntity.ok(
            ApiResponse.of(SnsRelationPageResponse.from(snsUseCase.getMyRelations(request.toQuery(principal.userId, kind)))),
        )
}
