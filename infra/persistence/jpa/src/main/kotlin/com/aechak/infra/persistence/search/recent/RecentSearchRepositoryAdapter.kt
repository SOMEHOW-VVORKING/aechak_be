package com.aechak.infra.persistence.search.recent

import com.aechak.domain.search.recent.RecentSearch
import com.aechak.domain.search.recent.repository.RecentSearchRepository
import org.springframework.data.domain.Limit
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

interface RecentSearchJpaRepository : JpaRepository<RecentSearch, Long> {
    fun findByUserIdOrderBySearchedAtDescIdDesc(
        userId: Long,
        limit: Limit,
    ): List<RecentSearch>
}

@Repository
class RecentSearchRepositoryAdapter(
    private val jpaRepository: RecentSearchJpaRepository,
) : RecentSearchRepository {
    override fun findRecentByUserId(
        userId: Long,
        limit: Int,
    ): List<RecentSearch> = jpaRepository.findByUserIdOrderBySearchedAtDescIdDesc(userId, Limit.of(limit))
}
