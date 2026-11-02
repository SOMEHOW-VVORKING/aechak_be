package com.aechak.application.notice.read.port

import com.aechak.application.notice.read.port.view.NoticeReadView
import com.aechak.domain.notice.notice.enums.NoticeAudience

/**
 * 독자용 공지 조회 포트.
 *
 * 두 메서드 모두 게시된 공지(status = PUBLISHED)와 독자 대상에 맞는 것만 돌려준다 —
 * 노출 판정을 포트 구현에 두어, 조건을 빠뜨린 호출자가 초안을 읽을 수 없게 한다.
 */
interface NoticeReadQueryPort {
    fun findPage(condition: NoticeReadCondition): List<NoticeReadView>

    /** 조건의 총건수 — 커서 앵커는 적용하지 않는다(첫 페이지에서만 쓴다). */
    fun count(condition: NoticeReadCondition): Long

    /** 게시되어 있고 그 독자가 볼 수 있는 공지만 돌려준다. 아니면 null. */
    fun findVisible(
        publicId: String,
        reader: NoticeAudience,
    ): NoticeReadView?
}
