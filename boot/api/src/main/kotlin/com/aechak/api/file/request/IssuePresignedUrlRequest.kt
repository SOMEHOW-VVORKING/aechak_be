package com.aechak.api.file.request

import com.aechak.application.file.port.enums.UploadPurpose
import com.aechak.application.file.usecase.command.IssuePresignedUrlCommand
import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.NotBlank

data class IssuePresignedUrlRequest(
    val purpose: UploadPurpose,
    @field:NotBlank(message = "contentType은 필수입니다.")
    @field:Schema(
        description = "지원하는 파일 형식: image/png, image/jpeg, image/webp, application/pdf",
        example = "image/png",
    )
    val contentType: String,
) {
    fun toCommand(userId: Long): IssuePresignedUrlCommand =
        IssuePresignedUrlCommand(
            purpose = purpose,
            contentType = contentType,
            userId = userId,
        )
}
