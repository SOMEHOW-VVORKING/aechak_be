package com.aechak.application.file.usecase.command

import com.aechak.application.file.port.enums.UploadPurpose

data class IssuePresignedUrlCommand(
    val purpose: UploadPurpose,
    val contentType: String,
    val userId: Long,
)
