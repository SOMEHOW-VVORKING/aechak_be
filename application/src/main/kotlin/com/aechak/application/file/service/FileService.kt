package com.aechak.application.file.service

import com.aechak.application.file.error.FileErrorCode
import com.aechak.application.file.port.FileKey
import com.aechak.application.file.port.FileStorage
import com.aechak.application.file.port.enums.FileType
import com.aechak.application.file.port.enums.UploadPurpose
import com.aechak.application.file.usecase.command.IssuePresignedUrlCommand
import com.aechak.application.file.usecase.command.PromoteFileCommand
import com.aechak.application.file.usecase.result.IssuePresignedUrlResult
import com.aechak.application.file.usecase.result.PromoteFileResult
import com.aechak.common.error.BusinessException
import org.springframework.stereotype.Service

@Service
class FileService(
    private val fileStorage: FileStorage,
) {
    fun issuePresignedUrl(command: IssuePresignedUrlCommand): IssuePresignedUrlResult {
        val fileType =
            FileType.from(command.contentType)
                ?: throw BusinessException(FileErrorCode.UNSUPPORTED_FILE_TYPE)
        if (fileType !in command.purpose.allowedFileTypes) {
            throw BusinessException(FileErrorCode.UNSUPPORTED_FILE_TYPE)
        }

        val issued = fileStorage.issueUploadUrl(command.purpose, fileType, command.userId)
        return IssuePresignedUrlResult(url = issued.url, key = issued.key)
    }

    /**
     * 이미지 소유자 검증 진행 후 승격
     */
    fun promote(command: PromoteFileCommand): PromoteFileResult {
        if (!command.tmpKey.startsWith(FileKey.tmpOwnerPrefix(command.userId))) {
            throw BusinessException(FileErrorCode.FILE_ACCESS_DENIED)
        }
        if (!command.tmpKey.startsWith(FileKey.tmpPrefixOf(command.userId, command.purpose))) {
            throw BusinessException(FileErrorCode.FILE_PURPOSE_MISMATCH)
        }
        val promotedKey = fileStorage.promote(command.tmpKey, command.purpose)
        return PromoteFileResult(key = promotedKey)
    }

    fun resolveMediaUrl(key: String?): String? = key?.let(fileStorage::publicUrlOf)

    /** 저장 key → 단기 다운로드 URL. 발급 자체는 무검증 — 호출 경로가 어드민 게이트 뒤로 한정된다. */
    fun issueDownloadUrl(
        key: String,
        purpose: UploadPurpose,
    ): String = fileStorage.issueDownloadUrl(key, purpose)
}
