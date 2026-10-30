package com.aechak.application.notice.read.port.view

import com.aechak.domain.notice.notice.enums.NoticeCategory
import java.time.LocalDateTime

/**
 * 독자용 공지 읽기 모델.
 *
 * 내부 메모·작성자 운영자 id·대상 값은 담지 않는다 — 운영 기록이라 독자에게 보일 것이 아니다.
 * 내부 id(noticeId)는 커서 타이브레이크에만 쓴다 — 응답 필드로는 싣지 않지만 커서 페이로드에는 실린다.
 */
data class NoticeReadView(
    val noticeId: Long,
    val publicId: String,
    val title: String,
    val content: String,
    val category: NoticeCategory,
    val pinned: Boolean,
    val publishedAt: LocalDateTime,
)
