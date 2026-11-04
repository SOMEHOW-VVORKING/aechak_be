package com.aechak.application.sns.service

import com.aechak.application.sns.port.SnsFeedQueryPort
import com.aechak.application.sns.port.view.SnsFeedEntryView
import com.aechak.application.sns.support.SnsFeedCursorCodec
import com.aechak.application.sns.usecase.query.SnsFeedQuery
import com.aechak.application.support.CursorPageResult
import com.aechak.application.support.CursorPageSize
import com.aechak.common.error.BusinessException
import com.aechak.common.error.CommonErrorCode
import com.aechak.domain.sns.post.Post
import com.aechak.domain.sns.post.repository.PostRepository
import org.springframework.stereotype.Service
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit

/** 피드 커서를 풀고 목록 포트로 한 페이지의 게시물을 고른다 */
@Service
class SnsFeedService(
    private val feedQueryPort: SnsFeedQueryPort,
    private val postRepository: PostRepository,
) {
    /** 탐색 피드 한 페이지. 첫 페이지는 지금이 기준 시각이고 다음 페이지는 커서의 기준 시각에서 7일을 센다 */
    fun explorePage(
        query: SnsFeedQuery,
        hiddenUserIds: Set<Long>,
    ): CursorPageResult<SnsFeedEntryView> {
        val cursor = query.cursor?.let(SnsFeedCursorCodec::decodeExplore)
        // 커서에 마이크로초까지 담으므로 첫 페이지도 같은 정밀도로 자른다
        val anchor = cursor?.anchor ?: LocalDateTime.now().truncatedTo(ChronoUnit.MICROS)
        val last = cursor?.let { findLastPost(it.lastPostPublicId) }
        val fetched =
            feedQueryPort.findExplorePage(
                query.viewerId,
                hiddenUserIds,
                anchor.minusDays(EXPLORE_DAYS),
                anchor,
                last?.createdAt,
                last?.id,
                CursorPageSize.fetchLimit(query.size),
            )
        return CursorPageResult.of(fetched = fetched, size = query.size) { SnsFeedCursorCodec.encodeExplore(anchor, it.publicId) }
    }

    /** 커서의 마지막 게시물. 그사이 지운 게시물도 위치로 쓰므로 상태를 보지 않는다 */
    private fun findLastPost(publicId: String): Post =
        postRepository.findByPublicId(publicId) ?: throw BusinessException(CommonErrorCode.INVALID_CURSOR)

    companion object {
        private const val EXPLORE_DAYS = 7L
    }
}
