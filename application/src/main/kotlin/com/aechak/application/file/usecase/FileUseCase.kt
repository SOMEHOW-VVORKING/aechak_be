package com.aechak.application.file.usecase

import com.aechak.application.file.usecase.command.IssuePresignedUrlCommand
import com.aechak.application.file.usecase.command.PromoteFileCommand
import com.aechak.application.file.usecase.command.ResolveMediaKeyCommand
import com.aechak.application.file.usecase.result.IssuePresignedUrlResult
import com.aechak.application.file.usecase.result.PromoteFileResult

interface FileUseCase {
    fun issuePresignedUrl(command: IssuePresignedUrlCommand): IssuePresignedUrlResult

    // tmp 내 파일을 정식 위치로 승격하고 저장할 키를 반환
    fun promote(command: PromoteFileCommand): PromoteFileResult

    /**
     * 전체 교체 시맨틱의 저장할 이미지 key 판정 — resolveMediaUrl(읽기)과 대칭인 쓰기 방향.
     * null=제거, 현재 key와 동일=유지(정식 key는 승격 대상이 아니라 스킵이 필수), 그 외=tmp 승격(소유·용도 검증).
     * S3 외부 호출이라 호출 도메인의 저장 트랜잭션 밖에서 먼저 부를 것.
     */
    fun resolveMediaKey(command: ResolveMediaKeyCommand): String?

    // 저장된 key → 표시용 URL(응답 조립용). null 키는 null 그대로 — "이미지 없음"
    fun resolveMediaUrl(key: String?): String?
}
