package com.aechak.seller.notice.response

import com.aechak.application.notice.read.usecase.result.NoticeSummaryResult
import com.aechak.application.support.CursorPageResult
import com.aechak.domain.notice.notice.enums.NoticeCategory
import java.time.LocalDateTime

/** 셀러센터 공지 목록 한 줄. enum은 그대로 싣는다 — 문자열화하면 생성 문서에 허용값이 실리지 않는다. */
data class SellerNoticeSummaryResponse(
    val noticeId: String,
    val title: String,
    val category: NoticeCategory,
    val pinned: Boolean,
    val contentPreview: String,
    val publishedAt: LocalDateTime,
) {
    companion object {
        fun from(result: NoticeSummaryResult): SellerNoticeSummaryResponse =
            SellerNoticeSummaryResponse(
                noticeId = result.publicId,
                title = result.title,
                category = result.category,
                pinned = result.pinned,
                contentPreview = result.contentPreview,
                publishedAt = result.publishedAt,
            )
    }
}

/** totalCount는 첫 페이지에서만 채워진다 — 뒤 페이지에서는 null. */
data class SellerNoticeListResponse(
    val items: List<SellerNoticeSummaryResponse>,
    val totalCount: Long?,
    val nextCursor: String?,
    val hasNext: Boolean,
) {
    companion object {
        fun from(result: CursorPageResult<NoticeSummaryResult>): SellerNoticeListResponse =
            SellerNoticeListResponse(
                items = result.items.map(SellerNoticeSummaryResponse::from),
                totalCount = result.totalCount,
                nextCursor = result.nextCursor,
                hasNext = result.hasNext,
            )
    }
}
