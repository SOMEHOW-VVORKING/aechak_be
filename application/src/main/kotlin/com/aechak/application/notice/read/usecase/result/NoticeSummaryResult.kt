package com.aechak.application.notice.read.usecase.result

import com.aechak.application.notice.read.port.view.NoticeReadView
import com.aechak.domain.notice.notice.enums.NoticeCategory
import java.time.LocalDateTime

/**
 * 목록 한 줄. 본문 앞머리만 싣는다 — 목록에서 전문을 내려보내면 공지 하나가 2000자다.
 *
 * @param contentPreview 본문 앞 [PREVIEW_LENGTH]자(코드 포인트 기준). 잘렸으면 끝에 줄임표가 붙는다.
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

        // (?U)가 없으면 유니코드 줄 구분자(U+2028·U+2029)와 줄바꿈 없는 공백(U+00A0)이 남는다.
        private val WHITESPACE_RUN = Regex("(?U)\\s+")

        fun from(view: NoticeReadView): NoticeSummaryResult =
            NoticeSummaryResult(
                publicId = view.publicId,
                title = view.title,
                category = view.category,
                pinned = view.pinned,
                contentPreview = preview(view.content),
                publishedAt = view.publishedAt,
            )

        /**
         * 줄바꿈이 섞인 본문이 목록에서 한 줄을 넘지 않게 공백으로 평탄화한 뒤 자른다.
         *
         * 절삭은 코드 포인트 경계에서 한다 — UTF-16 단위로 자르면 이모지 같은 보조 평면 문자가
         * 상·하위 서로게이트로 쪼개져, 응답에 짝 없는 서로게이트(`\uD83D`)가 실린다.
         */
        private fun preview(content: String): String {
            val flattened = content.replace(WHITESPACE_RUN, " ").trim()
            val codePoints = flattened.codePointCount(0, flattened.length)
            if (codePoints <= PREVIEW_LENGTH) {
                return flattened
            }
            return flattened.substring(0, flattened.offsetByCodePoints(0, PREVIEW_LENGTH)) + ELLIPSIS
        }
    }
}
