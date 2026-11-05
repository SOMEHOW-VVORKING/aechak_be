package com.aechak.application.sns.usecase

import com.aechak.application.sns.usecase.query.SnsFeedQuery
import com.aechak.application.sns.usecase.query.SnsPostTabQuery
import com.aechak.application.sns.usecase.result.SnsFeedItemResult
import com.aechak.application.sns.usecase.result.SnsPostGridItemResult
import com.aechak.application.support.CursorPageResult

interface SnsFeedUseCase {
    // 팔로잉 피드. 나와 내가 팔로우한 사용자의 원글과 리포스트를 원글마다 가장 늦은 이벤트에 한 번 싣는다. 로그인이 필요하다
    fun following(query: SnsFeedQuery): CursorPageResult<SnsFeedItemResult>

    // 탐색 피드. 기준 시각에서 7일 안의 원글을 최신순으로 주고 로그인이면 나, 팔로우, 차단, 뮤트한 사용자의 글을 뺀다.
    // 첫 페이지에서 7일 안의 글이 20개에 못 미치면 그 스크롤은 7일이 지난 글을 이어서 준다
    fun explore(query: SnsFeedQuery): CursorPageResult<SnsFeedItemResult>

    // 인기 피드. 72시간 안의 원글을 점수순으로 주고 20개에 못 미치면 72시간이 지난 글을 점수순으로 잇는다.
    // 첫 페이지를 부른 분의 순위를 다음 페이지가 30분 동안 따른다
    fun popular(query: SnsFeedQuery): CursorPageResult<SnsFeedItemResult>

    // 프로필 그리드 탭. 주인의 원글 전체를 최신순 칸으로 주고 뮤트는 거르지 않는다. 주인 판정은 팔로워 목록과 같다
    fun userPosts(query: SnsPostTabQuery): CursorPageResult<SnsPostGridItemResult>

    // 프로필 영상 탭. 그리드 탭과 같고 영상 원글만 준다
    fun userVideos(query: SnsPostTabQuery): CursorPageResult<SnsPostGridItemResult>

    // 프로필 리포스트 탭. 주인이 리포스트한 원글을 최근 리포스트순 칸으로 주고 보는 사람과 차단 관계인 작성자의 원글을 뺀다.
    // 보는 사람이 주인이 아니면 뮤트한 작성자의 원글도 뺀다. 주인 판정은 그리드 탭과 같다
    fun userReposts(query: SnsPostTabQuery): CursorPageResult<SnsPostGridItemResult>

    // 저장됨 탭. 내가 저장한 글을 최근 저장순 칸으로 주고 차단 관계이거나 뮤트한 작성자의 글을 뺀다. 로그인이 필요하다
    fun mySaves(query: SnsFeedQuery): CursorPageResult<SnsPostGridItemResult>
}
