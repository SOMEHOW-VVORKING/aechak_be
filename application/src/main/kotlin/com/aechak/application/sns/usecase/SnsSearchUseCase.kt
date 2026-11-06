package com.aechak.application.sns.usecase

import com.aechak.application.sns.usecase.query.SnsHashtagPostsQuery
import com.aechak.application.sns.usecase.query.SnsSearchQuery
import com.aechak.application.sns.usecase.result.SnsFeedItemResult
import com.aechak.application.sns.usecase.result.SnsHashtagPostPageResult
import com.aechak.application.support.CursorPageResult

interface SnsSearchUseCase {
    // 게시물 세그먼트. 캡션이나 해시태그에 단어가 모두 든 원글을 인기순이나 최신순으로 주고 첫 페이지에 결과 수를 싣는다
    fun searchPosts(query: SnsSearchQuery): CursorPageResult<SnsFeedItemResult>

    // 해시태그 게시물 목록. 그 태그가 달린 원글을 인기순이나 최신순으로 주고 태그의 게시글 수를 싣는다
    fun hashtagPosts(query: SnsHashtagPostsQuery): SnsHashtagPostPageResult
}
