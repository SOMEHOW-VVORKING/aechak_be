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
        purpose: UploadPurpose,
    ): String

    /** 저장된 키를 클라이언트에 전달할 공개 URL로 변환 */
    fun publicUrlOf(key: String): String

    /** 저장된 객체를 삭제 */
    fun delete(
        key: String,
        purpose: UploadPurpose,
    )
}

data class IssueFileUrl(
    val url: String,
    val key: String,
)
