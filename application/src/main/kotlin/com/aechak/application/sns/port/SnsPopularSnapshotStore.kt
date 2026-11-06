package com.aechak.application.sns.port

/** 분마다 하나 남기는 인기 피드 순위. 지워도 다시 계산하는 캐시라 SNS 원본 데이터가 아니다 */
interface SnsPopularSnapshotStore {
    /** 그 분의 순위 게시물 id. 키가 없거나 만료됐으면 null */
    fun find(epochMinute: Long): List<Long>?

    /** 그 분의 키가 없을 때만 순위를 남긴다. 키는 31분 뒤 만료돼 그 분의 마지막에 첫 페이지를 부른 사람도 30분 동안 이어 내린다 */
    fun saveIfAbsent(
        epochMinute: Long,
        postIds: List<Long>,
    )
}
