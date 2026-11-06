package com.aechak.application.sns.usecase.query

import com.aechak.application.support.CursorPageSize
import com.aechak.domain.sns.hashtag.Hashtag
import java.text.Normalizer

/** keyword는 검색창 입력값 그대로이고 viewerId가 null이면 비로그인이다 */
data class SnsSearchQuery(
    val viewerId: Long?,
    val keyword: String,
    val sort: SnsSearchSort = SnsSearchSort.POPULAR,
    val cursor: String? = null,
    val size: Int = CursorPageSize.DEFAULT,
) {
    init {
        require(size in CursorPageSize.MIN..CursorPageSize.MAX) { "size는 ${CursorPageSize.MIN}~${CursorPageSize.MAX} 범위 안에 있어야 합니다." }
    }

    /** 앞뒤 공백을 지우고 NFC로 맞춘 뒤 앞의 # 하나를 뗀 검색어 */
    private val text: String = Normalizer.normalize(keyword.trim(), Normalizer.Form.NFC).removePrefix("#").trim()

    /** 공백으로 나눈 단어. 둘째 단어부터는 앞의 #를 캡션 글자로 남긴다 */
    val words: List<String> = text.split(WHITESPACE).filter { it.isNotEmpty() }

    /** 단어를 해시태그 표기(앞의 # 하나를 떼고 영문 소문자)로 바꾼 값 */
    val tagWords: List<String> = words.map(Hashtag::clean)

    /** 공백을 모두 뺀 검색어. 계정과 해시태그 세그먼트가 쓴다 */
    val compact: String = text.replace(WHITESPACE, "")

    /** 해시태그 세그먼트가 찾는 이름 */
    val compactTag: String = Hashtag.clean(compact)

    companion object {
        const val MAX_LENGTH = 100
        private val WHITESPACE = Regex("\\s+")
    }
}

/** viewerId가 null이면 비로그인이다 */
data class SnsHashtagPostsQuery(
    val viewerId: Long?,
    val name: String,
    val sort: SnsSearchSort = SnsSearchSort.POPULAR,
    val cursor: String? = null,
    val size: Int = CursorPageSize.DEFAULT,
) {
    init {
        require(size in CursorPageSize.MIN..CursorPageSize.MAX) { "size는 ${CursorPageSize.MIN}~${CursorPageSize.MAX} 범위 안에 있어야 합니다." }
    }
}

enum class SnsSearchSort {
    POPULAR,
    LATEST,
}
