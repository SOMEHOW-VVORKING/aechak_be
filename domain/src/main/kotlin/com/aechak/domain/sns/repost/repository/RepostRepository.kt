package com.aechak.domain.sns.repost.repository

import java.time.LocalDateTime

/** 리포스트 관계 저장소 포트 */
interface RepostRepository {
    /** 리포스트 원자 삽입, 새로 들어갔으면 true. 두 시각 열은 at으로 채운다 */
    fun insertIfAbsent(
        reposterId: Long,
        postId: Long,
        at: LocalDateTime,
    ): Boolean

    /** reposter가 한 리포스트 하드 삭제, 삭제된 행 수 반환 */
    fun delete(
        reposterId: Long,
        postId: Long,
    ): Int

    /** reposter가 author의 글에 한 리포스트를 원글 상태와 관계없이 지우고 실제로 지운 행의 원글 id를 돌려준다 */
    fun deleteBetween(
        reposterId: Long,
        authorId: Long,
    ): List<Long>
}
