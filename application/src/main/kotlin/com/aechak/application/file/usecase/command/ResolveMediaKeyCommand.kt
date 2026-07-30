package com.aechak.application.file.usecase.command

import com.aechak.application.file.port.enums.UploadPurpose

/**
 * 전체 교체 시맨틱의 이미지 key 판정 입력 — currentKey는 호출 도메인이 자기 저장소에서 읽어 값으로 넘긴다
 * (file 도메인은 현재 key가 어디 사는지 모른다).
 */
data class ResolveMediaKeyCommand(
    val currentKey: String?,
    val requestedKey: String?,
    val userId: Long,
    val purpose: UploadPurpose,
)
