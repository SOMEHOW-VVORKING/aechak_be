package com.aechak.api.file.request

import com.aechak.application.file.port.enums.UploadPurpose
import com.aechak.application.file.usecase.command.IssuePresignedUrlCommand
import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.Min
import jakarta.validation.constraints.NotBlank

data class IssuePresignedUrlRequest(
    val purpose: UploadPurpose,
    @field:NotBlank(message = "contentType은 필수입니다.")
    @field:Schema(
        description = "지원하는 파일 형식: image/png, image/jpeg, image/webp, video/mp4, video/quicktime, application/pdf",
        example = "image/png",
    )
    val contentType: String,
    @field:Min(value = 1, message = "contentLength는 1 이상이어야 합니다.")
    @field:Schema(
        description = "올릴 파일의 바이트 수. SNS_POST_IMAGE와 SNS_POST_VIDEO는 필수이고 이 길이 그대로 PUT해야 한다",
        example = "1843200",
    )
    val contentLength: Long? = null,
) {
    fun toCommand(userId: Long): IssuePresignedUrlCommand =
        IssuePresignedUrlCommand(
            purpose = purpose,
            contentType = contentType,
            userId = userId,
            contentLength = contentLength,
        )
}
