package com.aechak.application.notice.read.usecase.result

import com.aechak.application.notice.read.port.view.NoticeReadView
import com.aechak.domain.notice.notice.enums.NoticeCategory
import java.time.LocalDateTime

/**
 * 목록 한 줄. 본문 앞머리만 싣는다 — 목록에서 전문을 내려보내면 공지 하나가 2000자다.
 *
 * @param contentPreview 본문 앞 [PREVIEW_LENGTH]자. 잘렸으면 끝에 줄임표가 붙는다.
 * @param pinned 상단 고정 여부. 읽는 쪽 화면이 '중요' 표기로도 쓴다.
 */
data class NoticeSummaryResult(
    val publicId: String,
    val title: String,
    val category: NoticeCategory,
    val pinned: Boolean,
    val contentPreview: String,
    val publishedAt: LocalDateTime,
) {
    companion object {
        const val PREVIEW_LENGTH = 100
        private const val ELLIPSIS = "…"

        fun from(view: NoticeReadView): NoticeSummaryResult =
            NoticeSummaryResult(
                publicId = view.publicId,
                title = view.title,
                category = view.category,
                pinned = view.pinned,
                contentPreview = preview(view.content),
                publishedAt = view.publishedAt,
            )

        /** 줄바꿈이 섞인 본문이 목록에서 한 줄을 넘지 않게 공백으로 평탄화한 뒤 자른다. */
        private fun preview(content: String): String {
            val flattened = content.replace(Regex("\\s+"), " ").trim()
            return if (flattened.length <= PREVIEW_LENGTH) flattened else flattened.take(PREVIEW_LENGTH) + ELLIPSIS
        }
    }
}
