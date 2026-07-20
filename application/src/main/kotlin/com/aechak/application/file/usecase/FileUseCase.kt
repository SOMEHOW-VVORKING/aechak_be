package com.aechak.application.file.usecase

import com.aechak.application.file.usecase.command.IssuePresignedUrlCommand
import com.aechak.application.file.usecase.result.IssuePresignedUrlResult

interface FileUseCase {
    fun issuePresignedUrl(command: IssuePresignedUrlCommand): IssuePresignedUrlResult
}
