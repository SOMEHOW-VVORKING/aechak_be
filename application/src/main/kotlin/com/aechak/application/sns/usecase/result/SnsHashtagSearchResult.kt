package com.aechak.application.sns.usecase.result

import com.aechak.domain.sns.hashtag.Hashtag

/** 해시태그 자동완성. query는 표기를 정리한 검색어이고 exact는 EXISTS일 때만 있으며 items에 다시 나오지 않는다 */
data class SnsHashtagSearchResult(
    val query: String,
    val status: SnsHashtagSearchStatus,
    val exact: SnsHashtagResult?,
    val items: List<SnsHashtagResult>,
)

data class SnsHashtagResult(
    val name: String,
    val postCount: Int,
) {
    companion object {
        fun from(hashtag: Hashtag): SnsHashtagResult = SnsHashtagResult(hashtag.name, hashtag.postCount)
    }
}

/** 해시태그 모달이 그릴 줄. INVALID, TOO_LONG, BANNED면 안내만 보여주고 태그를 추가하지 않는다 */
enum class SnsHashtagSearchStatus {
    EMPTY,
    EXISTS,

    // 게시글 수가 0으로 남은 태그도 새 태그로 본다
    NEW,
    INVALID,
    TOO_LONG,
    BANNED,
}
