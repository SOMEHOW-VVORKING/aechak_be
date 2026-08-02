package com.aechak.infra.persistence.search.recent

import com.aechak.domain.search.recent.RecentSearch
import com.aechak.domain.search.recent.repository.RecentSearchRepository
import org.springframework.data.domain.Limit
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository

interface RecentSearchJpaRepository : JpaRepository<RecentSearch, Long> {
    fun findByUserIdOrderBySearchedAtDescIdDesc(
        userId: Long,
        limit: Limit,
    ): List<RecentSearch>

    @Modifying
    @Query("delete from RecentSearch r where r.id = :id and r.userId = :userId")
    fun deleteByIdAndUserId(
        @Param("id") id: Long,
        @Param("userId") userId: Long,
    ): Int

    @Modifying
    @Query("delete from RecentSearch r where r.userId = :userId")
    fun deleteAllByUserId(
        @Param("userId") userId: Long,
    ): Int
}

@Repository
class RecentSearchRepositoryAdapter(
    private val jpaRepository: RecentSearchJpaRepository,
) : RecentSearchRepository {
    override fun findRecentByUserId(
        userId: Long,
        limit: Int,
    ): List<RecentSearch> = jpaRepository.findByUserIdOrderBySearchedAtDescIdDesc(userId, Limit.of(limit))

    override fun delete(
        userId: Long,
        id: Long,
    ) {
        jpaRepository.deleteByIdAndUserId(id, userId)
    }

    override fun deleteAll(userId: Long) {
        jpaRepository.deleteAllByUserId(userId)
    }
}
