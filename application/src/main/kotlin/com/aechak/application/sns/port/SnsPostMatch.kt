package com.aechak.application.sns.port

/** 검색 목록이 고르는 원글 */
sealed interface SnsPostMatch {
    /** 단어마다 캡션이나 그 글의 해시태그 이름에 들어 있는 글. tagWords는 words를 해시태그 표기로 바꾼 값으로 순서가 같다 */
    data class Words(
        val words: List<String>,
        val tagWords: List<String>,
    ) : SnsPostMatch

    /** 이 해시태그가 달린 글 */
    data class Tag(
        val hashtagId: Long,
    ) : SnsPostMatch
}
