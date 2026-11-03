package com.aechak.application.file.port

import com.aechak.application.file.port.enums.FileType
import com.aechak.application.file.port.enums.UploadPurpose

interface FileStorage {
    /** contentLength가 있으면 PUT 서명에 넣어 길이가 다른 업로드를 S3가 거부하게 한다 */
    fun issueUploadUrl(
        purpose: UploadPurpose,
        fileType: FileType,
        userId: Long,
        contentLength: Long?,
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

    /** 저장된 key → 단기 다운로드 URL(presigned GET) — 어드민 서류 열람용. 서명은 네트워크 왕복 없는 로컬 연산. */
    fun issueDownloadUrl(
        key: String,
        purpose: UploadPurpose,
    ): String

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
