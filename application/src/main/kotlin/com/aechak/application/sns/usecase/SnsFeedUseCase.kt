package com.aechak.application.sns.usecase

import com.aechak.application.sns.usecase.query.SnsFeedQuery
import com.aechak.application.sns.usecase.result.SnsFeedItemResult
import com.aechak.application.support.CursorPageResult

/** SNS 피드 진입점. 응답 페이지에는 totalCount가 없다 */
interface SnsFeedUseCase {
    // 탐색 피드. 기준 시각에서 7일 안의 원글을 최신순으로 주고 로그인이면 나, 팔로우, 차단, 뮤트한 사용자의 글을 뺀다
    fun explore(query: SnsFeedQuery): CursorPageResult<SnsFeedItemResult>
}
