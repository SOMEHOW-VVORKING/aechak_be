package com.aechak.application.file.usecase.command

import com.aechak.application.file.port.enums.UploadPurpose

/** DB에 저장된 키로 파일을 삭제할 때 사용하는 입력값 */
data class DeleteFileCommand(
    val key: String,
    val purpose: UploadPurpose,
)
