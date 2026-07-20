package com.aechak.api.file.controller

import com.aechak.api.file.request.IssuePresignedUrlRequest
import com.aechak.api.file.response.PresignedUrlResponse
import com.aechak.application.file.usecase.FileUseCase
import com.aechak.webcommon.response.ApiResponse
import com.aechak.websecurity.authentication.AuthPrincipal
import jakarta.validation.Valid
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/files")
class FileController(
    private val fileUseCase: FileUseCase,
) {
    @PostMapping("/presigned-url")
    fun issuePresignedUrl(
        @Valid @RequestBody request: IssuePresignedUrlRequest,
        @AuthenticationPrincipal principal: AuthPrincipal,
    ): ResponseEntity<ApiResponse<PresignedUrlResponse>> {
        val result = fileUseCase.issuePresignedUrl(request.toCommand(principal.userId))
        return ResponseEntity.ok(ApiResponse.of(PresignedUrlResponse.from(result)))
    }
}
