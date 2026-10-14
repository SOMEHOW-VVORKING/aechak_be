package com.aechak.application.support

/** 오프셋 페이지네이션 공용 출력 래퍼 — items + 필터 기준 전체 행 수 */
data class PageResult<T>(
    val items: List<T>,
    val totalCount: Long,
)
