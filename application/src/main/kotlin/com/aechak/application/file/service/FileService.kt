package com.aechak.application.file.service

import com.aechak.application.file.error.FileErrorCode
import com.aechak.application.file.port.FileStorage
import com.aechak.application.file.port.enums.FileType
import com.aechak.application.file.usecase.command.IssuePresignedUrlCommand
import com.aechak.application.file.usecase.result.IssuePresignedUrlResult
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
}
