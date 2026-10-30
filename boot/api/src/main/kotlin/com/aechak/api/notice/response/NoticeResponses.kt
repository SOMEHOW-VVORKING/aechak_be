package com.aechak.api.notice.response

import com.aechak.application.notice.read.usecase.result.NoticeDetailResult
import com.aechak.application.notice.read.usecase.result.NoticeSummaryResult
import com.aechak.application.support.CursorPageResult
import com.aechak.domain.notice.notice.enums.NoticeCategory
import java.time.LocalDateTime

/** 공지 목록 한 줄. enum은 그대로 싣는다 — 문자열화하면 생성 문서에 허용값이 실리지 않는다. */
data class NoticeSummaryResponse(
    val noticeId: String,
    val title: String,
    val category: NoticeCategory,
    val pinned: Boolean,
    val contentPreview: String,
    val publishedAt: LocalDateTime,
) {
    companion object {
        fun from(result: NoticeSummaryResult): NoticeSummaryResponse =
            NoticeSummaryResponse(
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
data class NoticeListResponse(
    val items: List<NoticeSummaryResponse>,
    val totalCount: Long?,
    val nextCursor: String?,
    val hasNext: Boolean,
) {
    companion object {
        fun from(result: CursorPageResult<NoticeSummaryResult>): NoticeListResponse =
            NoticeListResponse(
                items = result.items.map(NoticeSummaryResponse::from),
                totalCount = result.totalCount,
                nextCursor = result.nextCursor,
                hasNext = result.hasNext,
            )
    }
}

/** 공지 상세 — 전문까지. 내부 메모·노출 대상·작성 운영자는 담지 않는다. */
data class NoticeDetailResponse(
    val noticeId: String,
    val title: String,
    val content: String,
    val category: NoticeCategory,
    val pinned: Boolean,
    val publishedAt: LocalDateTime,
) {
    companion object {
        fun from(result: NoticeDetailResult): NoticeDetailResponse =
            NoticeDetailResponse(
                noticeId = result.publicId,
                title = result.title,
                content = result.content,
                category = result.category,
                pinned = result.pinned,
                publishedAt = result.publishedAt,
            )
    }
}
