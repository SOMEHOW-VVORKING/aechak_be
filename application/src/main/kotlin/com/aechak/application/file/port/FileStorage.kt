package com.aechak.application.file.port

import com.aechak.application.file.port.enums.FileType
import com.aechak.application.file.port.enums.UploadPurpose

interface FileStorage {
    fun issueUploadUrl(
        purpose: UploadPurpose,
        fileType: FileType,
        userId: Long,
    ): IssueFileUrl

    /**
     * tmp에서 승격해서 새 키를 반환함
     */
    fun promote(
        tmpKey: String,
        userId: Long,
        purpose: UploadPurpose,
    ): String
}

data class IssueFileUrl(
    val url: String,
    val key: String,
)
