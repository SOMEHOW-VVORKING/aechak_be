package com.aechak.application.file.service

import com.aechak.application.file.error.FileErrorCode
import com.aechak.application.file.port.FileKey
import com.aechak.application.file.port.FileStorage
import com.aechak.application.file.port.enums.FileType
import com.aechak.application.file.usecase.command.IssuePresignedUrlCommand
import com.aechak.application.file.usecase.command.PromoteFileCommand
import com.aechak.application.file.usecase.command.ResolveMediaKeyCommand
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

    /**
     * 전체 교체 시맨틱의 저장할 key 판정 — null=제거, 현재 key와 동일=유지, 그 외=tmp 승격.
     * 유지 스킵은 최적화가 아니라 필수: 정식 key는 tmp 접두 검증을 통과할 수 없어 promote가 거절한다.
     */
    fun resolveMediaKey(command: ResolveMediaKeyCommand): String? =
        command.requestedKey?.let { requested ->
            if (requested == command.currentKey) {
                requested
            } else {
                promote(PromoteFileCommand(requested, command.userId, command.purpose)).key
            }
        }

    fun resolveMediaUrl(key: String?): String? = key?.let(fileStorage::publicUrlOf)
}
