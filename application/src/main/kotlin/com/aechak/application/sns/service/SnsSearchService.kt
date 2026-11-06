package com.aechak.application.sns.service

import com.aechak.application.sns.port.SnsPostMatch
import com.aechak.application.sns.port.SnsSearchQueryPort
import com.aechak.application.sns.support.PopularScore
import com.aechak.application.sns.support.SnsSearchCursorCodec
import com.aechak.application.support.CursorPageResult
import com.aechak.application.support.CursorPageSize
import com.aechak.common.error.BusinessException
import com.aechak.common.error.CommonErrorCode
import com.aechak.domain.sns.hashtag.Hashtag
import com.aechak.domain.sns.hashtag.repository.HashtagRepository
import com.aechak.domain.sns.post.repository.PostRepository
import org.springframework.stereotype.Service

@Service
class SnsSearchService(
    private val searchQueryPort: SnsSearchQueryPort,
    private val postRepository: PostRepository,
    private val hashtagRepository: HashtagRepository,
) {
    /** 최신순 한 페이지의 게시물 id. 커서의 마지막 게시물은 그사이 지워졌어도 위치로 쓴다 */
    fun latestPage(
        match: SnsPostMatch,
        scope: String,
        cursor: String?,
        size: Int,
        hiddenUserIds: Set<Long>,
    ): CursorPageResult<Long> {
        val last =
            cursor?.let {
                postRepository.findByPublicId(SnsSearchCursorCodec.decodeLatest(it, scope))
                    ?: throw BusinessException(CommonErrorCode.INVALID_CURSOR)
            }
        val fetched = searchQueryPort.findLatestPage(match, hiddenUserIds, last?.createdAt, last?.id, CursorPageSize.fetchLimit(size))
        return CursorPageResult
            .of(
                fetched = fetched,
                size = size,
            ) { SnsSearchCursorCodec.encodeLatest(scope, it.publicId) }
            .map { it.postId }
    }

    /** 인기순 순위. 맞는 원글 전체를 기간과 감쇠 없이 점수순으로 최대 1,000개 고르고 보는 사람의 숨김은 넣지 않는다 */
    fun popularRanking(match: SnsPostMatch): List<Long> =
        searchQueryPort.findPopularRanking(match, PopularScore.REPOST, PopularScore.MAX_RANK)

    fun count(match: SnsPostMatch): Long = searchQueryPort.count(match)

    fun findHashtag(name: String): Hashtag? = hashtagRepository.findByName(name)
}
