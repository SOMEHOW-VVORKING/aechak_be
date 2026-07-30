package com.aechak.infra.persistence.search.recent

import com.aechak.domain.search.recent.RecentSearch
import com.aechak.domain.search.recent.repository.RecentSearchRepository
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

interface RecentSearchJpaRepository : JpaRepository<RecentSearch, Long> {
    fun findByUserIdOrderBySearchedAtDescIdDesc(
        userId: Long,
        pageable: Pageable,
    ): List<RecentSearch>
}

@Repository
class RecentSearchRepositoryAdapter(
    private val jpaRepository: RecentSearchJpaRepository,
) : RecentSearchRepository {
    override fun findRecentByUserId(
        userId: Long,
        limit: Int,
    ): List<RecentSearch> = jpaRepository.findByUserIdOrderBySearchedAtDescIdDesc(userId, PageRequest.of(0, limit))
}
