package com.aechak.application.file.usecase

import com.aechak.application.file.usecase.command.IssuePresignedUrlCommand
import com.aechak.application.file.usecase.command.PromoteFileCommand
import com.aechak.application.file.usecase.result.IssuePresignedUrlResult
import com.aechak.application.file.usecase.result.PromoteFileResult

interface FileUseCase {
    fun issuePresignedUrl(command: IssuePresignedUrlCommand): IssuePresignedUrlResult

    // tmp 내 파일을 정식 위치로 승격하고 저장할 키를 반환
    fun promote(command: PromoteFileCommand): PromoteFileResult
}
