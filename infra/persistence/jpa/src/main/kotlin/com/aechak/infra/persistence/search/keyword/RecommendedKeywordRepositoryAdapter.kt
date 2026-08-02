package com.aechak.infra.persistence.search.keyword

import com.aechak.domain.search.keyword.RecommendedKeyword
import com.aechak.domain.search.keyword.repository.RecommendedKeywordRepository
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

interface RecommendedKeywordJpaRepository : JpaRepository<RecommendedKeyword, Long> {
    fun findByIsActiveTrueOrderBySortOrderAscIdAsc(): List<RecommendedKeyword>
}

@Repository
class RecommendedKeywordRepositoryAdapter(
    private val jpaRepository: RecommendedKeywordJpaRepository,
) : RecommendedKeywordRepository {
    override fun findActiveOrderBySortOrder(): List<RecommendedKeyword> = jpaRepository.findByIsActiveTrueOrderBySortOrderAscIdAsc()
}
