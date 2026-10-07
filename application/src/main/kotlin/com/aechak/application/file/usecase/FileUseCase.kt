package com.aechak.application.file.usecase

import com.aechak.application.file.usecase.command.DeleteFileCommand
import com.aechak.application.file.usecase.command.IssuePresignedUrlCommand
import com.aechak.application.file.usecase.command.PromoteFileCommand
import com.aechak.application.file.usecase.result.IssuePresignedUrlResult
import com.aechak.application.file.usecase.result.PromoteFileResult

interface FileUseCase {
    fun issuePresignedUrl(command: IssuePresignedUrlCommand): IssuePresignedUrlResult

    // 임시 파일을 정식 위치로 옮기고 저장할 키를 반환
    fun promote(command: PromoteFileCommand): PromoteFileResult

    // 저장된 키를 공개 URL로 변환. 키가 없으면 null을 반환
    fun resolveMediaUrl(key: String?): String?

    // 저장된 객체를 삭제
    fun delete(command: DeleteFileCommand)
}
