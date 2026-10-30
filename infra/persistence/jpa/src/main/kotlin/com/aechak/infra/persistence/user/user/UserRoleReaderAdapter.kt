package com.aechak.infra.persistence.user.user

import com.aechak.application.auth.port.UserRoleReader
import com.aechak.domain.user.user.enums.UserRole
import org.springframework.stereotype.Repository

/**
 * UserRoleReader 포트의 JPA 어댑터 — PK 인덱스 단발 프로젝션 조회로 고정한다.
 * 엔티티(+eager 프로필) 로딩 없이 role 한 컬럼만 읽는다.
 */
@Repository
class UserRoleReaderAdapter(
    private val jpaRepository: UserJpaRepository,
) : UserRoleReader {
    override fun roleOf(userId: Long): UserRole? = jpaRepository.findRoleById(userId)
}
