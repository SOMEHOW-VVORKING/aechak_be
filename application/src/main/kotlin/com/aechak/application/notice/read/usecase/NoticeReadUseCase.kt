package com.aechak.application.notice.read.usecase

import com.aechak.application.notice.read.usecase.query.NoticeListQuery
import com.aechak.application.notice.read.usecase.result.NoticeDetailResult
import com.aechak.application.notice.read.usecase.result.NoticeSummaryResult
import com.aechak.application.support.CursorPageResult
import com.aechak.domain.notice.notice.enums.NoticeAudience

/**
 * 공지 읽기 진입점 — 구매자 앱과 셀러센터가 함께 쓴다.
 *
 * 독자 신분(reader)을 인자로 받는다. 호출자가 자기 경로에 맞는 값을 넘기고(구매자 호스트는 BUYER,
 * 셀러센터는 SELLER) 포트 구현이 `audience IN (ALL, reader)`로 잠근다 — 신분을 요청 본문이나
 * 쿼리 파라미터로 받지 않는 이유는 그러면 독자가 자기 신분을 바꿔 부를 수 있기 때문이다.
 */
interface NoticeReadUseCase {
    fun list(
        reader: NoticeAudience,
        query: NoticeListQuery,
    ): CursorPageResult<NoticeSummaryResult>

    /** 게시되지 않았거나 그 독자 대상이 아니면 150000(공지 없음)으로 끊는다. */
    fun detail(
        reader: NoticeAudience,
        publicId: String,
    ): NoticeDetailResult
}
