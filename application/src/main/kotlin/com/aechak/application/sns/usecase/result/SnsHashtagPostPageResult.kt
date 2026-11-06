package com.aechak.application.sns.usecase.result

import com.aechak.application.support.CursorPageResult

/** 해시태그 게시물 목록. hashtag는 정규화한 이름과 게시글 수이고 없는 태그는 0이다 */
data class SnsHashtagPostPageResult(
    val hashtag: SnsHashtagResult,
    val page: CursorPageResult<SnsFeedItemResult>,
)
