package com.aechak.application.file.usecase

import com.aechak.application.file.port.enums.UploadPurpose
import com.aechak.application.file.usecase.command.IssuePresignedUrlCommand
import com.aechak.application.file.usecase.command.PromoteFileCommand
import com.aechak.application.file.usecase.result.IssuePresignedUrlResult
import com.aechak.application.file.usecase.result.PromoteFileResult

interface FileUseCase {
    fun issuePresignedUrl(command: IssuePresignedUrlCommand): IssuePresignedUrlResult

    // tmp 내 파일을 정식 위치로 승격하고 저장할 키를 반환. S3 외부 호출이라 호출 도메인의 저장 트랜잭션 밖에서 부를 것
    fun promote(command: PromoteFileCommand): PromoteFileResult

    // 저장된 key → 표시용 URL(응답 조립용). null 키는 null 그대로 — "이미지 없음"
    fun resolveMediaUrl(key: String?): String?

    // 저장된 key → 단기 다운로드 URL(어드민 서류 열람). presign은 로컬 서명이라 조회 트랜잭션 안 호출 허용
    fun issueDownloadUrl(
        key: String,
        purpose: UploadPurpose,
    ): String
}
