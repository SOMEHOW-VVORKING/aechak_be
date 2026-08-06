package com.aechak.application.user.user.usecase.result

import com.aechak.domain.user.user.enums.UserStatus

/**
 * 내 정보 — PENDING이면 프로필 계열은 null, email은 소셜 미제공(애플 가림 등)이면 null.
 * profileImageKey는 프로필 수정 PUT의 "유지" 회신용 핸들 — 표시는 profileImageUrl이 담당한다.
 * phoneNumber는 표시용 문자열 — 휴대폰 인증 전까지 항상 null(마스킹 여부·형식은 서버 정책).
 */
data class UserMeResult(
    val status: UserStatus,
    val nickname: String?,
    val profileImageUrl: String?,
    val profileImageKey: String?,
    val bio: String?,
    val email: String?,
    val phoneNumber: String?,
    val isPhoneVerified: Boolean,
)
