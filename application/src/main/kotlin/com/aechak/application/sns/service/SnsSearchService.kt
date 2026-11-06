package com.aechak.application.sns.service

import com.aechak.application.sns.port.SnsPostMatch
import com.aechak.application.sns.port.SnsSearchQueryPort
import com.aechak.application.sns.port.view.SnsSearchHashtagView
import com.aechak.application.sns.port.view.SnsSearchUserView
import com.aechak.application.sns.support.PopularScore
import com.aechak.application.sns.support.SnsSearchCursorCodec
import com.aechak.application.sns.usecase.query.SnsSearchQuery
import com.aechak.application.support.CursorPageResult
import com.aechak.application.support.CursorPageSize
import com.aechak.common.error.BusinessException
import com.aechak.common.error.CommonErrorCode
import com.aechak.domain.sns.hashtag.Hashtag
import com.aechak.domain.sns.hashtag.repository.HashtagRepository
import com.aechak.domain.sns.post.repository.PostRepository
import org.springframework.stereotype.Service
import java.util.Locale

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

    /** 계정 세그먼트 한 페이지. 공백을 뺀 검색어가 비거나 12자를 넘으면 쿼리 없이 빈 페이지다 */
    fun userPage(query: SnsSearchQuery): CursorPageResult<SnsSearchUserView> {
        val nickname = query.compact
        // 닉네임은 대소문자를 가리지 않고 비교해 커서 범위도 소문자로 묶는다
        val scope = USERS_SCOPE + nickname.lowercase(Locale.ROOT)
        val last = query.cursor?.let { SnsSearchCursorCodec.decodeUsers(it, scope) }
        if (nickname.isEmpty() || nickname.codePointCount(0, nickname.length) > NICKNAME_MAX) return emptyPage()
        val fetched =
            searchQueryPort.findUserPage(
                nickname,
                query.viewerId,
                last?.bucket,
                last?.followerCount,
                last?.lastPublicId,
                CursorPageSize.fetchLimit(query.size),
            )
        return CursorPageResult.of(fetched = fetched, size = query.size) {
            SnsSearchCursorCodec.encodeUsers(scope, SnsSearchCursorCodec.UserCursor(it.bucket, it.followerCount, it.publicId))
        }
    }

    /** 해시태그 세그먼트 한 페이지. 정규화한 검색어가 비거나 30자를 넘으면 쿼리 없이 빈 페이지다 */
    fun hashtagPage(query: SnsSearchQuery): CursorPageResult<SnsSearchHashtagView> {
        val name = query.compactTag
        val scope = HASHTAGS_SCOPE + name
        val last = query.cursor?.let { SnsSearchCursorCodec.decodeHashtags(it, scope) }
        if (name.isEmpty() || Hashtag.isTooLong(name)) return emptyPage()
        val fetched = searchQueryPort.findHashtagPage(name, last?.postCount, last?.lastName, CursorPageSize.fetchLimit(query.size))
        return CursorPageResult.of(fetched = fetched, size = query.size) {
            SnsSearchCursorCodec.encodeHashtags(scope, SnsSearchCursorCodec.HashtagCursor(it.postCount, it.name))
        }
    }

    private fun <T> emptyPage(): CursorPageResult<T> = CursorPageResult(emptyList(), null, null, false)

    companion object {
        private const val NICKNAME_MAX = 12
        private const val USERS_SCOPE = "u\u001F"
        private const val HASHTAGS_SCOPE = "h\u001F"
    }
}
