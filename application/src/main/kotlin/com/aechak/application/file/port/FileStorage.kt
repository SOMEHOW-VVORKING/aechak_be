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

    /** 저장된 key → 표시용 공개 URL(CDN) — 응답 조립 시 사용. */
    fun publicUrlOf(key: String): String

    /** 저장된 key → 단기 다운로드 URL(presigned GET) — 어드민 서류 열람용. 서명은 네트워크 왕복 없는 로컬 연산. */
    fun issueDownloadUrl(
        key: String,
        purpose: UploadPurpose,
    ): String
}

data class IssueFileUrl(
    val url: String,
    val key: String,
)
