package com.aechak.infra.persistence.search.recent

import com.aechak.domain.search.recent.RecentSearch
import com.aechak.domain.search.recent.repository.RecentSearchRepository
import org.springframework.data.domain.Limit
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository
import java.time.LocalDateTime

interface RecentSearchJpaRepository : JpaRepository<RecentSearch, Long> {
    /** (user_id, keyword) 유일 제약 기준 원자적 upsert. 검색 시각은 앱 시각, 감사 컬럼은 DB NOW(6) */
    @Modifying
    @Query(
        value = """
            INSERT INTO recent_searches (user_id, keyword, searched_at, created_at, updated_at)
            VALUES (:userId, :keyword, :searchedAt, NOW(6), NOW(6))
            ON DUPLICATE KEY UPDATE
                searched_at = GREATEST(searched_at, VALUES(searched_at)),
                updated_at = NOW(6)
        """,
        nativeQuery = true,
    )
    fun upsert(
        @Param("userId") userId: Long,
        @Param("keyword") keyword: String,
        @Param("searchedAt") searchedAt: LocalDateTime,
    ): Int

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
    override fun record(
        userId: Long,
        keyword: String,
        searchedAt: LocalDateTime,
    ) {
        jpaRepository.upsert(userId, keyword, searchedAt)
    }

    override fun findRecentByUserId(
        userId: Long,
        limit: Int,
    ): List<RecentSearch> = jpaRepository.findByUserIdOrderBySearchedAtDescIdDesc(userId, Limit.of(limit))

    override fun delete(
        userId: Long,
        id: Long,
    ): Int = jpaRepository.deleteByIdAndUserId(id, userId)

    override fun deleteAll(userId: Long) {
        jpaRepository.deleteAllByUserId(userId)
    }
}
