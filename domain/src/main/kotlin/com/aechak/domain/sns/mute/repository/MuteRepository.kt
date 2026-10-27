package com.aechak.domain.sns.mute.repository

/** 뮤트 관계 저장소 포트 */
interface MuteRepository {
    /** 뮤트 원자 삽입, 새로 들어갔으면 true */
    fun insertIfAbsent(
        muterId: Long,
        mutedId: Long,
    ): Boolean

    /** muter가 건 뮤트만 하드 삭제, 삭제된 행 수 반환 */
    fun delete(
        muterId: Long,
        mutedId: Long,
    ): Int
}
