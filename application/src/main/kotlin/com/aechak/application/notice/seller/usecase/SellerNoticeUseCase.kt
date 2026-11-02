package com.aechak.application.notice.seller.usecase

import com.aechak.application.notice.read.usecase.query.NoticeListQuery
import com.aechak.application.notice.read.usecase.result.NoticeDetailResult
import com.aechak.application.notice.read.usecase.result.NoticeSummaryResult
import com.aechak.application.support.CursorPageResult

/**
 * 셀러센터 공지 조회 진입점 — 셀러 자격 게이트를 통과한 뒤 공지 읽기로 넘긴다.
 *
 * 공지 BC가 아니라 조립 자리에 두는 이유: 자격 판정의 주인은 seller BC라 그 에러코드(10004)를
 * 던져야 하는데, 공지 BC가 남의 BC 코드를 던지면 BC 격리가 깨진다. 셀러센터만 이 진입점을 쓴다.
 */
interface SellerNoticeUseCase {
    fun list(
        userId: Long,
        query: NoticeListQuery,
    ): CursorPageResult<NoticeSummaryResult>

    fun detail(
        userId: Long,
        publicId: String,
    ): NoticeDetailResult
}
