package com.aechak.application.file.facade

import com.aechak.application.file.service.FileService
import com.aechak.application.file.usecase.FileUseCase
import com.aechak.application.file.usecase.command.IssuePresignedUrlCommand
import com.aechak.application.file.usecase.command.PromoteFileCommand
import com.aechak.application.file.usecase.command.ResolveMediaKeyCommand
import com.aechak.application.file.usecase.result.IssuePresignedUrlResult
import com.aechak.application.file.usecase.result.PromoteFileResult
import org.springframework.stereotype.Service

@Service
class FileFacade(
    private val fileService: FileService,
) : FileUseCase {
    override fun issuePresignedUrl(command: IssuePresignedUrlCommand): IssuePresignedUrlResult = fileService.issuePresignedUrl(command)

    override fun promote(command: PromoteFileCommand): PromoteFileResult = fileService.promote(command)

    override fun resolveMediaKey(command: ResolveMediaKeyCommand): String? = fileService.resolveMediaKey(command)

    override fun resolveMediaUrl(key: String?): String? = fileService.resolveMediaUrl(key)
}
