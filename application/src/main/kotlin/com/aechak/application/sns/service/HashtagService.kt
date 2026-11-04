package com.aechak.application.sns.service

import com.aechak.application.sns.usecase.result.SnsHashtagResult
import com.aechak.application.sns.usecase.result.SnsHashtagSearchResult
import com.aechak.application.sns.usecase.result.SnsHashtagSearchStatus
import com.aechak.domain.sns.hashtag.Hashtag
import com.aechak.domain.sns.hashtag.repository.HashtagRepository
import org.springframework.stereotype.Service

@Service
class HashtagService(
    private val hashtagRepository: HashtagRepository,
) {
    /** 태그 id들의 이름을 받은 순서대로 돌려준다 */
    fun namesOf(hashtagIds: List<Long>): List<String> {
        val names = hashtagRepository.findAllById(hashtagIds).associate { it.id to it.name }
        return hashtagIds.mapNotNull(names::get)
    }

    /**
     * 해시태그 자동완성. query는 표기를 정리한 검색어이고 비면 추천 태그, 글자 규칙을 어기면 빈 목록, 아니면 이름에 검색어가 든 태그를 준다.
     * 보는 사람의 차단과 뮤트는 반영하지 않는다.
     */
    fun search(
        query: String,
        bannedQuery: Boolean,
        size: Int,
    ): SnsHashtagSearchResult {
        when {
            query.isEmpty() -> return SnsHashtagSearchResult(query, SnsHashtagSearchStatus.EMPTY, null, findPopular(query, size))
            !Hashtag.hasAllowedChars(query) -> return SnsHashtagSearchResult(query, SnsHashtagSearchStatus.INVALID, null, emptyList())
            Hashtag.isTooLong(query) -> return SnsHashtagSearchResult(query, SnsHashtagSearchStatus.TOO_LONG, null, emptyList())
            bannedQuery -> return SnsHashtagSearchResult(query, SnsHashtagSearchStatus.BANNED, null, findPopular(query, size))
        }
        val exact = hashtagRepository.findByName(query)?.takeIf { it.postCount > 0 }?.let(SnsHashtagResult::from)
        val status = if (exact == null) SnsHashtagSearchStatus.NEW else SnsHashtagSearchStatus.EXISTS
        // 이름이 검색어와 같은 태그는 exact로만 싣는다
        return SnsHashtagSearchResult(query, status, exact, findPopular(query, size).filterNot { it.name == query })
    }

    private fun findPopular(
        keyword: String,
        size: Int,
    ): List<SnsHashtagResult> = hashtagRepository.findPopular(keyword, size).map(SnsHashtagResult::from)
}
