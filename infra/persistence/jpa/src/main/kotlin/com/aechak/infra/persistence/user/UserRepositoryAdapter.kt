package com.aechak.infra.persistence.user

import com.aechak.domain.user.user.User
import com.aechak.domain.user.user.repository.UserRepository
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Repository

/** Spring Data 인터페이스는 이 모듈 밖으로 노출되지 않는다 — 어댑터의 내부 부품. */
interface UserJpaRepository : JpaRepository<User, Long>

/**
 * domain 포트(UserRepository)의 JPA 어댑터 — 각 도메인이 따라갈 어댑터 템플릿.
 * 포트 시그니처(도메인 타입)를 그대로 구현한다 — Pageable 등 Spring 타입을 포트로 역류시키지 않는다.
 */
@Repository
class UserRepositoryAdapter(
    private val jpaRepository: UserJpaRepository,
) : UserRepository {
    override fun findById(id: Long): User? = jpaRepository.findByIdOrNull(id)

    override fun save(user: User): User = jpaRepository.save(user)
}
