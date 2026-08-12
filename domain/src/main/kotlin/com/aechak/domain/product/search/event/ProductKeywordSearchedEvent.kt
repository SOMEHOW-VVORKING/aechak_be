package com.aechak.domain.product.search.event

import com.aechak.domain.support.DomainEvent

/**
 * 로그인 사용자가 키워드로 상품을 검색했다는 사실 이벤트
 * 트리거 : 최근 검색어 적재
 */
data class ProductKeywordSearchedEvent(
    val searcherId: Long,
    val keyword: String,
) : DomainEvent
