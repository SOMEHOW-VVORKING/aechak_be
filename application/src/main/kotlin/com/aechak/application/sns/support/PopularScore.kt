package com.aechak.application.sns.support

/**
 * 인기 점수 가중치와 순위 크기. 점수는 좋아요 수, 공개 댓글을 단 사람 수, 리포스트 수의 가중합이고 시간 감쇠가 없다.
 * 지금은 리포스트 항만 쓰고 좋아요와 댓글 항은 그 기능이 생기면 점수 쿼리에 추가한다. 검색 인기순도 같은 가중치를 쓴다.
 * 저장과 작성자 본인의 반응은 점수에 넣지 않는다.
 */
object PopularScore {
    const val LIKE = 1
    const val COMMENTER = 2
    const val REPOST = 3

    /** 순위에 남기는 게시물 수 상한. 인기 커서의 자리도 이 범위 안이다 */
    const val MAX_RANK = 1_000
}
