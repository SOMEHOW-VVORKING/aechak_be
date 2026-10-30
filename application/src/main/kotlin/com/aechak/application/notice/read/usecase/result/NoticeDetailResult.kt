package com.aechak.application.notice.read.usecase.result

import com.aechak.application.notice.read.port.view.NoticeReadView
import com.aechak.domain.notice.notice.enums.NoticeCategory
import java.time.LocalDateTime

/**
 * 독자용 공지 상세 — 전문까지.
 *
 * 내부 메모·노출 대상·작성 운영자는 담지 않는다. 운영 기록이라 독자에게 보일 것이 아니고,
 * 노출 대상은 이 응답이 내려온 사실 자체로 이미 충족된 조건이다.
 */
data class NoticeDetailResult(
    val publicId: String,
    val title: String,
    val content: String,
    val category: NoticeCategory,
    val pinned: Boolean,
    val publishedAt: LocalDateTime,
) {
    companion object {
        fun from(view: NoticeReadView): NoticeDetailResult =
            NoticeDetailResult(
                publicId = view.publicId,
                title = view.title,
                content = view.content,
                category = view.category,
                pinned = view.pinned,
                publishedAt = view.publishedAt,
            )
    }
}
