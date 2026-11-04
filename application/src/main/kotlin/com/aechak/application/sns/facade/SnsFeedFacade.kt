package com.aechak.application.sns.facade

import com.aechak.application.sns.service.PostCardAssembler
import com.aechak.application.sns.service.SnsFeedService
import com.aechak.application.sns.service.SnsVisibilityService
import com.aechak.application.sns.usecase.SnsFeedUseCase
import com.aechak.application.sns.usecase.query.SnsFeedQuery
import com.aechak.application.sns.usecase.result.SnsFeedItemResult
import com.aechak.application.support.CursorPageResult
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/** SNS 피드 진입점. 금지어 판정과 파일 승격을 쓰지 않아 게시물 쪽 SnsPostFacade와 나눠 둔다 */
@Service
class SnsFeedFacade(
    private val snsFeedService: SnsFeedService,
    private val snsVisibilityService: SnsVisibilityService,
    private val postCardAssembler: PostCardAssembler,
) : SnsFeedUseCase {
    @Transactional(readOnly = true)
    override fun explore(query: SnsFeedQuery): CursorPageResult<SnsFeedItemResult> {
        val mutedIds = snsVisibilityService.findMutedIds(query.viewerId)
        val page = snsFeedService.explorePage(query, snsVisibilityService.findViewerFilter(query.viewerId) + mutedIds)
        val cards = postCardAssembler.assemble(page.items.map { it.postId }, query.viewerId, mutedIds)
        return CursorPageResult(cards.map { SnsFeedItemResult(it, repostedBy = null) }, null, page.nextCursor, page.hasNext)
    }
}
