package com.aechak.application.sns.port

import com.aechak.application.sns.port.view.SnsCommentView

/**
 * 댓글 가시성 판정이 한 조건식에 모인 조회 포트. 목록, 단건, 답글 대상, 수가 같은 조건을 쓴다.
 * 읽기 메서드만 users, user_profiles를 함께 읽는다.
 */
interface SnsCommentQueryPort {
    /** 보이는 최상위 댓글을 lastRootId 다음부터 오래된순으로 limit개 */
    fun findRootPage(
        scope: SnsCommentScope,
        lastRootId: Long?,
        limit: Int,
    ): List<SnsCommentView>

    /** rootIds 아래 보이는 대댓글 전부를 최상위 댓글 id, 대댓글 id 순으로 */
    fun findReplies(
        scope: SnsCommentScope,
        rootIds: List<Long>,
    ): List<SnsCommentView>
}

/**
 * 한 게시물의 댓글을 보는 사람 기준으로 거르는 입력. viewerId가 null이면 비로그인이다.
 * hidden은 보는 사람과 어느 쪽으로든 차단 관계인 사용자, muted는 보는 사람이 뮤트한 사용자다.
 */
data class SnsCommentScope(
    val postId: Long,
    val postAuthorId: Long,
    val viewerId: Long?,
    val hidden: Set<Long>,
    val muted: Set<Long>,
)
