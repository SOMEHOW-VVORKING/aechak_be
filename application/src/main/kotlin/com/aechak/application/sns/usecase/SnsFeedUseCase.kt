package com.aechak.application.sns.usecase

import com.aechak.application.sns.usecase.query.SnsFeedQuery
import com.aechak.application.sns.usecase.result.SnsFeedItemResult
import com.aechak.application.support.CursorPageResult

/** SNS 피드 진입점. 응답 페이지에는 totalCount가 없다 */
interface SnsFeedUseCase {
    // 탐색 피드. 기준 시각에서 7일 안의 원글을 최신순으로 주고 로그인이면 나, 팔로우, 차단, 뮤트한 사용자의 글을 뺀다
    fun explore(query: SnsFeedQuery): CursorPageResult<SnsFeedItemResult>

    // 인기 피드. 72시간 안의 원글을 점수순으로 주고 첫 페이지를 부른 분의 순위를 다음 페이지가 30분 동안 따른다
    fun popular(query: SnsFeedQuery): CursorPageResult<SnsFeedItemResult>
}
