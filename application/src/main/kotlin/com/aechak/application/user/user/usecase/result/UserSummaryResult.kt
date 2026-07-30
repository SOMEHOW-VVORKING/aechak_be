package com.aechak.application.user.user.usecase.result

import com.aechak.domain.user.user.User

/**
 * 목록/요약 조회 전용 Result — 상세용(UserResult)과 분리해 필드 규모를 다르게 가져간다.
 * 목록 API가 상세 Result를 재사용하면 필드가 끌려 들어와 비대해진다 — 처음부터 분리한다.
 */
data class UserSummaryResult(
    val userId: Long,
    val nickname: String,
) {
    companion object {
        // nickname은 ERD상 user_profiles 소속이라 애그리거트 자식(user.profile)에서 읽는다.
        fun from(user: User): UserSummaryResult = UserSummaryResult(user.id, user.profile.nickname)
    }
}
