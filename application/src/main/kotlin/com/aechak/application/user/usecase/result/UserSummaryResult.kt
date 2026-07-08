package com.aechak.application.user.usecase.result

import com.aechak.domain.user.User

/**
 * 목록/요약 조회 전용 Result — 상세용(UserResult)과 분리해 필드 규모를 다르게 가져간다.
 * 목록 API가 상세 Result를 재사용하면 필드가 끌려 들어와 비대해진다 — 처음부터 분리한다.
 */
data class UserSummaryResult(
    val userId: Long,
    val nickname: String,
) {
    companion object {
        fun from(user: User): UserSummaryResult = UserSummaryResult(user.id, user.nickname)
    }
}
