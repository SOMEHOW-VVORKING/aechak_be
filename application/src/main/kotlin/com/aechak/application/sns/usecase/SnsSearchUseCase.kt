package com.aechak.application.sns.usecase

import com.aechak.application.sns.usecase.query.SnsHashtagPostsQuery
import com.aechak.application.sns.usecase.query.SnsSearchQuery
import com.aechak.application.sns.usecase.result.SnsFeedItemResult
import com.aechak.application.sns.usecase.result.SnsHashtagPostPageResult
import com.aechak.application.sns.usecase.result.SnsHashtagResult
import com.aechak.application.sns.usecase.result.SnsSearchUserResult
import com.aechak.application.support.CursorPageResult

interface SnsSearchUseCase {
    // 게시물 세그먼트. 캡션이나 해시태그에 단어가 모두 든 원글을 인기순이나 최신순으로 주고 첫 페이지에 결과 수를 싣는다
    fun searchPosts(query: SnsSearchQuery): CursorPageResult<SnsFeedItemResult>

    // 해시태그 게시물 목록. 그 태그가 달린 원글을 인기순이나 최신순으로 주고 태그의 게시글 수를 싣는다
    fun hashtagPosts(query: SnsHashtagPostsQuery): SnsHashtagPostPageResult

    // 계정 세그먼트. 닉네임에 검색어가 든 사용자를 같음, 시작, 포함 순서와 팔로워 수로 주고 나를 차단한 사용자는 기본 프로필로 준다
    fun searchUsers(query: SnsSearchQuery): CursorPageResult<SnsSearchUserResult>

    // 해시태그 세그먼트. 이름에 검색어가 든 태그를 게시글 수 순서로 준다
    fun searchHashtags(query: SnsSearchQuery): CursorPageResult<SnsHashtagResult>
}
