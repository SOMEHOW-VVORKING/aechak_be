package com.aechak.domain.search.keyword.repository

import com.aechak.domain.search.keyword.RecommendedKeyword

interface RecommendedKeywordRepository {
    /** 활성(is_active=true) 추천 검색어를 노출 순서(sort_order asc)대로 조회한다. */
    fun findActiveOrderBySortOrder(): List<RecommendedKeyword>
}
