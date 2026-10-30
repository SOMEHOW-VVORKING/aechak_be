package com.aechak.application.notice.read.usecase.result

import com.aechak.application.notice.read.port.view.NoticeReadView
import com.aechak.domain.notice.notice.enums.NoticeCategory
import java.time.LocalDateTime
import kotlin.test.Test
import kotlin.test.assertEquals

/** 목록 미리보기의 평탄화·절삭 경계를 고정한다 — 둘 다 응답 문자열이 깨지는 자리다. */
class NoticeSummaryResultTest {
    @Test
    fun `상한을 넘는 본문은 잘리고 줄임표가 붙는다`() {
        val preview = previewOf("가".repeat(150))

        assertEquals("가".repeat(NoticeSummaryResult.PREVIEW_LENGTH) + "…", preview)
    }

    @Test
    fun `상한과 같은 본문은 그대로 주고 줄임표를 붙이지 않는다`() {
        val content = "가".repeat(NoticeSummaryResult.PREVIEW_LENGTH)

        assertEquals(content, previewOf(content))
    }

    /**
     * UTF-16 단위로 자르면 경계에 걸친 이모지가 상·하위 서로게이트로 쪼개져, 짝 없는 서로게이트가
     * 응답에 실린다(Jackson이 `\uD83D`로 그대로 내보내 화면에는 깨진 글자가 남는다).
     */
    @Test
    fun `경계에 걸친 이모지가 반쪽으로 쪼개지지 않는다`() {
        val content = "가".repeat(NoticeSummaryResult.PREVIEW_LENGTH - 1) + EMOJI + "나"

        val preview = previewOf(content)

        assertEquals("가".repeat(NoticeSummaryResult.PREVIEW_LENGTH - 1) + EMOJI + "…", preview)
        // 짝 없는 서로게이트는 UTF-8로 인코딩될 때 '?'로 치환된다 — 왕복이 같으면 성한 문자열이다.
        assertEquals(preview, String(preview.toByteArray(Charsets.UTF_8), Charsets.UTF_8))
    }

    /** 절삭 길이는 코드 포인트로 센다 — 이모지 하나를 두 자로 세면 이모지 목록이 절반에서 잘린다. */
    @Test
    fun `이모지는 한 자로 세어 상한까지는 잘리지 않는다`() {
        val content = EMOJI.repeat(NoticeSummaryResult.PREVIEW_LENGTH)

        assertEquals(content, previewOf(content))
    }

    @Test
    fun `줄바꿈과 연속 공백은 한 칸으로 평탄화된다`() {
        assertEquals("첫 문단 둘째 문단", previewOf("첫 문단\n\n  둘째 문단"))
    }

    /** 기본 `\s`는 이 셋을 공백으로 보지 않아, 평탄화를 통과한 미리보기가 목록에서 줄을 넘는다. */
    @Test
    fun `유니코드 줄 구분자와 줄바꿈 없는 공백도 평탄화된다`() {
        assertEquals("첫 문단 둘째 문단", previewOf("첫 문단 둘째 문단"))
        assertEquals("첫 문단 둘째 문단", previewOf("첫 문단 둘째 문단"))
        assertEquals("첫 문단 둘째 문단", previewOf("첫 문단 둘째 문단"))
    }

    private fun previewOf(content: String): String = NoticeSummaryResult.from(viewOf(content)).contentPreview

    private fun viewOf(content: String): NoticeReadView =
        NoticeReadView(
            noticeId = 1L,
            publicId = "01NOTICE000000000000000001",
            title = "공지 제목",
            content = content,
            category = NoticeCategory.SERVICE,
            pinned = false,
            publishedAt = PUBLISHED_AT,
        )

    private companion object {
        const val EMOJI = "😀"
        val PUBLISHED_AT: LocalDateTime = LocalDateTime.of(2026, 10, 30, 10, 0)
    }
}
