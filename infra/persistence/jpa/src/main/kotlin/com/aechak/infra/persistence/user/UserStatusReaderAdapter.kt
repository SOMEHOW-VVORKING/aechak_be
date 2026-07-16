package com.aechak.infra.persistence.user

import com.aechak.application.auth.port.UserStatusReader
import com.aechak.domain.user.user.enums.UserStatus
import org.springframework.stereotype.Repository

/** UserStatusReader 포트의 JPA 어댑터 — PK 인덱스 단발 프로젝션 조회로 고정한다. */
@Repository
class UserStatusReaderAdapter(
    private val jpaRepository: UserJpaRepository,
) : UserStatusReader {

    override fun statusOf(userId: Long): UserStatus? = jpaRepository.findStatusById(userId)
}
