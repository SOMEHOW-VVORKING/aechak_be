package com.aechak.application.user.usecase.result

import com.aechak.domain.user.User

/**
 * UseCase 반환 전용 모델 — 도메인 엔티티를 밖으로 내보내지 않기 위해 존재한다
 * (lazy 직렬화 사고·내부 필드 유출·엔티티가 API 계약이 되는 문제 차단).
 *
 * - 엔티티 → Result 변환은 companion의 from()으로 이 파일에 둔다.
 * - 목록/요약용이 필요해지면 SummaryResult로 분리해 필드 규모를 다르게 가져간다.
 */
data class UserResult(
    val userId: Long,
    val nickname: String,
) {
    companion object {
        fun from(user: User): UserResult = UserResult(user.id, user.nickname)
    }
}
