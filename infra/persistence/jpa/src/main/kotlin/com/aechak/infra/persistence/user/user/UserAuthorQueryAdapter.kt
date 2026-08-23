package com.aechak.infra.persistence.user.user

import com.aechak.application.user.user.port.UserAuthorQueryPort
import com.aechak.application.user.user.port.view.UserAuthorView
import org.springframework.stereotype.Repository

/** UserAuthorQueryPort의 JPA 어댑터 — 빈 목록은 쿼리 없이 돌려준다. */
@Repository
class UserAuthorQueryAdapter(
    private val jpaRepository: UserJpaRepository,
) : UserAuthorQueryPort {
    override fun findAuthorsByIds(ids: Collection<Long>): List<UserAuthorView> =
        if (ids.isEmpty()) emptyList() else jpaRepository.findAuthorsByIds(ids)
}
