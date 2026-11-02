package com.aechak.application.notice.read.facade

import com.aechak.application.notice.read.service.NoticeReadService
import com.aechak.application.notice.read.usecase.NoticeReadUseCase
import com.aechak.application.notice.read.usecase.query.NoticeListQuery
import com.aechak.application.notice.read.usecase.result.NoticeDetailResult
import com.aechak.application.notice.read.usecase.result.NoticeSummaryResult
import com.aechak.application.support.CursorPageResult
import com.aechak.domain.notice.notice.enums.NoticeAudience
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/** NoticeReadUseCase의 유일한 구현체. 읽기 전용 트랜잭션 경계는 여기 고정. */
@Service
class NoticeReadFacade(
    private val noticeReadService: NoticeReadService,
) : NoticeReadUseCase {
    @Transactional(readOnly = true)
    override fun list(
        reader: NoticeAudience,
        query: NoticeListQuery,
    ): CursorPageResult<NoticeSummaryResult> = noticeReadService.findPage(reader, query).map(NoticeSummaryResult::from)

    @Transactional(readOnly = true)
    override fun detail(
        reader: NoticeAudience,
        publicId: String,
    ): NoticeDetailResult = NoticeDetailResult.from(noticeReadService.getVisible(reader, publicId))
}
