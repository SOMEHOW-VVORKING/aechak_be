package com.aechak.application.file.facade

import com.aechak.application.file.port.enums.UploadPurpose
import com.aechak.application.file.service.FileService
import com.aechak.application.file.support.FileRateLimitRule
import com.aechak.application.file.usecase.FileUseCase
import com.aechak.application.file.usecase.command.DeleteFileCommand
import com.aechak.application.file.usecase.command.IssuePresignedUrlCommand
import com.aechak.application.file.usecase.command.PromoteFileCommand
import com.aechak.application.file.usecase.result.IssuePresignedUrlResult
import com.aechak.application.file.usecase.result.PromoteFileResult
import com.aechak.application.ratelimit.usecase.RateLimitUseCase
import org.springframework.stereotype.Service

@Service
class FileFacade(
    private val fileService: FileService,
    // 빈도 제한은 api에만 있다. 업로드 주소를 발급하지 않는 admin, seller, batch에서는 null이다
    private val rateLimitUseCase: RateLimitUseCase?,
) : FileUseCase {
    override fun issuePresignedUrl(command: IssuePresignedUrlCommand): IssuePresignedUrlResult {
        checkNotNull(rateLimitUseCase) { "빈도 제한이 없는 실행 모듈에서 업로드 주소를 발급할 수 없습니다" }
            .check(command.userId, FileRateLimitRule.ISSUE_UPLOAD_URL)
        return fileService.issuePresignedUrl(command)
    }

    override fun promote(command: PromoteFileCommand): PromoteFileResult = fileService.promote(command)

    override fun resolveMediaUrl(key: String?): String? = fileService.resolveMediaUrl(key)

    override fun issueDownloadUrl(
        key: String,
        purpose: UploadPurpose,
    ): String = fileService.issueDownloadUrl(key, purpose)

    override fun delete(command: DeleteFileCommand) = fileService.delete(command)
}
