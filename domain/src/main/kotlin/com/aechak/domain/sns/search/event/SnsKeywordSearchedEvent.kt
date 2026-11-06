package com.aechak.domain.sns.search.event

import com.aechak.domain.support.DomainEvent

/**
 * 로그인 사용자가 SNS 게시물 검색 첫 페이지를 조회했다는 사실 이벤트. keyword는 검색창 입력값 그대로다
 * 트리거 : 최근 검색어 적재
 */
data class SnsKeywordSearchedEvent(
    val searcherId: Long,
    val keyword: String,
) : DomainEvent
