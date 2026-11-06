package com.aechak.domain.sns.mute.repository

/** 뮤트 관계 저장소 포트 */
interface MuteRepository {
    /** 뮤트 원자 삽입. 이미 뮤트했으면 그대로 둔다 */
    fun insertIfAbsent(
        muterId: Long,
        mutedId: Long,
    )

    /** muter가 건 뮤트만 하드 삭제, 삭제된 행 수 반환 */
    fun delete(
        muterId: Long,
        mutedId: Long,
    ): Int
}
