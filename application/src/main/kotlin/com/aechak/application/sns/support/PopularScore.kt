package com.aechak.application.sns.support

/**
 * 인기 점수 가중치와 순위 크기. 점수는 카드의 좋아요 수, 공개 댓글 수, 리포스트 수의 가중합이고 시간 감쇠가 없다.
 * 지금은 댓글과 리포스트 항을 쓰고 좋아요 항은 좋아요 기능이 생기면 점수 쿼리에 추가한다. 검색 인기순도 같은 가중치를 쓴다.
 * 작성자 본인의 반응도 카드의 수처럼 넣고 저장은 넣지 않는다.
 */
object PopularScore {
    const val LIKE = 1
    const val COMMENT = 2
    const val REPOST = 3

    /** 순위에 남기는 게시물 수 상한. 인기 커서의 자리도 이 범위 안이다 */
    const val MAX_RANK = 1_000
}
