package com.aechak.infra.persistence.user.user

import com.aechak.application.auth.port.UserStatusReader
import com.aechak.domain.user.user.enums.UserStatus
import org.springframework.stereotype.Repository
import java.time.LocalDateTime

/**
 * UserStatusReader 포트의 JPA 어댑터 — PK 인덱스 단발 프로젝션 조회로 고정한다.
 * 저장값과 제재 만료 시각을 함께 떠 와 도메인 규칙(UserStatus.effective)으로 유효 상태를 만든다 —
 * 호출부(필터·토큰 회전)가 만료 판정을 각자 반복하지 않게 포트가 여기서 끝낸다.
 */
@Repository
class UserStatusReaderAdapter(
    private val jpaRepository: UserJpaRepository,
) : UserStatusReader {
    override fun statusOf(userId: Long): UserStatus? {
        val projection = jpaRepository.findAccessStatusById(userId) ?: return null
        return UserStatus.effective(projection.status, projection.sanctionUntil, LocalDateTime.now())
    }
}

/** 인가 게이트 전용 프로젝션 — 엔티티(+eager 프로필) 로딩 없이 두 컬럼만 읽는다. */
data class UserAccessStatusProjection(
    val status: UserStatus,
    val sanctionUntil: LocalDateTime?,
)
