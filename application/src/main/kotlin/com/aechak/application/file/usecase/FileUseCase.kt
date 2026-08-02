package com.aechak.application.file.usecase

import com.aechak.application.file.usecase.command.IssuePresignedUrlCommand
import com.aechak.application.file.usecase.command.PromoteFileCommand
import com.aechak.application.file.usecase.result.IssuePresignedUrlResult
import com.aechak.application.file.usecase.result.PromoteFileResult

interface FileUseCase {
    fun issuePresignedUrl(command: IssuePresignedUrlCommand): IssuePresignedUrlResult

    // tmp 내 파일을 정식 위치로 승격하고 저장할 키를 반환
    fun promote(command: PromoteFileCommand): PromoteFileResult

    // 저장된 key → 표시용 URL(응답 조립용). null 키는 null 그대로 — "이미지 없음"
    fun resolveMediaUrl(key: String?): String?
}
