package com.aechak.application.sns.port

/** 검색어나 태그마다 분마다 하나 남기는 인기순 순위. 지워도 다시 계산하는 캐시다 */
interface SnsSearchRankingStore {
    /** 그 범위와 분의 순위 게시물 id. 키가 없거나 만료됐으면 null */
    fun find(
        scope: String,
        epochMinute: Long,
    ): List<Long>?

    /** 키가 없을 때만 순위를 남긴다. 키는 31분 뒤 만료된다 */
    fun saveIfAbsent(
        scope: String,
        epochMinute: Long,
        postIds: List<Long>,
    )
}
