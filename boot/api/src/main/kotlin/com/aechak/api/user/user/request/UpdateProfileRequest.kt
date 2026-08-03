package com.aechak.api.user.user.request

import com.aechak.application.user.user.usecase.command.UpdateProfileCommand
import jakarta.validation.constraints.Size

/**
 * 전체 교체 PUT — 계약상 세 필드 항상 전송(유지할 값도 실어 보냄), nullable은 null=제거.
 * 닉네임 형식 규칙(2~12자·허용 문자)은 도메인이 판정한다(30003) — 여기서 검증하면 90002로 오라벨된다.
 */
data class UpdateProfileRequest(
    val nickname: String,
    @field:Size(max = 150, message = "자기소개는 {max}자를 넘을 수 없습니다.")
    val bio: String?,
    // S3 key 상한(1024B)·DB 컬럼(varchar 1024)과 동일 — 초과분은 S3 SDK 예외(500) 전에 여기서 400으로 자른다
    @field:Size(max = 1024, message = "이미지 키는 {max}자를 넘을 수 없습니다.")
    val profileImageKey: String?,
) {
    fun toCommand(userId: Long): UpdateProfileCommand =
        UpdateProfileCommand(
            userId = userId,
            nickname = nickname,
            bio = bio,
            profileImageKey = profileImageKey,
        )
}
