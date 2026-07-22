package com.aechak.application.file.usecase.command

import com.aechak.application.file.port.enums.UploadPurpose

data class PromoteFileCommand(
    val tmpKey: String,
    val userId: Long,
    val purpose: UploadPurpose,
)
