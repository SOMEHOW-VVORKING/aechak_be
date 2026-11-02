package com.aechak.seller.notice.response

import com.aechak.application.notice.read.usecase.result.NoticeDetailResult
import com.aechak.domain.notice.notice.enums.NoticeCategory
import java.time.LocalDateTime

/**
 * 셀러센터 공지 상세 — 전문까지.
 *
 * 내부 메모·노출 대상·작성 운영자는 담지 않는다. 읽음 여부도 없다 —
 * 사용자별 열람 기록을 남기는 테이블이 아직 없다.
 */
data class SellerNoticeDetailResponse(
    val noticeId: String,
    val title: String,
    val content: String,
    val category: NoticeCategory,
    val pinned: Boolean,
    val publishedAt: LocalDateTime,
) {
    companion object {
        fun from(result: NoticeDetailResult): SellerNoticeDetailResponse =
            SellerNoticeDetailResponse(
                noticeId = result.publicId,
                title = result.title,
                content = result.content,
                category = result.category,
                pinned = result.pinned,
                publishedAt = result.publishedAt,
            )
    }
}
