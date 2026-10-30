package com.aechak.application.notice.read.port

import com.aechak.domain.notice.notice.enums.NoticeAudience
import com.aechak.domain.notice.notice.enums.NoticeCategory
import java.time.LocalDateTime

/**
 * 독자용 공지 목록 조회 조건.
 *
 * - reader는 독자의 신분(BUYER 또는 SELLER). 포트 구현이 `audience IN (ALL, reader)`로 잠근다 —
 *   이 조건이 구매자와 셀러를 가르는 유일한 축이라 nullable로 두지 않는다.
 * - category가 null이면 분류 조건 미적용
 * - anchor가 있으면 그 지점 다음부터 — 정렬은 (pinned desc, published_at desc, id desc) 고정
 */
data class NoticeReadCondition(
    val reader: NoticeAudience,
    val category: NoticeCategory?,
    val anchor: NoticeReadAnchor?,
    val limit: Int,
) {
    init {
        require(reader in NoticeAudience.READER_AUDIENCES) { "독자 대상은 BUYER 또는 SELLER여야 합니다." }
        require(limit > 0) { "limit은 양수여야 합니다." }
    }
}

/**
 * 커서가 가리키는 마지막 행. 정렬 키 세 개를 모두 든다 —
 * pinned를 빼면 고정 공지 묶음과 일반 공지 묶음의 경계에서 페이지가 어긋난다.
 */
data class NoticeReadAnchor(
    val lastPinned: Boolean,
    val lastPublishedAt: LocalDateTime,
    val lastNoticeId: Long,
)
