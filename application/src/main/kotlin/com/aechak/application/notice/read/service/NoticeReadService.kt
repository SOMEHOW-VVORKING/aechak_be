package com.aechak.application.notice.read.service

import com.aechak.application.notice.read.port.NoticeReadCondition
import com.aechak.application.notice.read.port.NoticeReadQueryPort
import com.aechak.application.notice.read.port.view.NoticeReadView
import com.aechak.application.notice.read.support.NoticeReadCursorCodec
import com.aechak.application.notice.read.usecase.query.NoticeListQuery
import com.aechak.application.support.CursorPageResult
import com.aechak.application.support.CursorPageSize
import com.aechak.common.error.BusinessException
import com.aechak.domain.notice.error.NoticeErrorCode
import com.aechak.domain.notice.notice.enums.NoticeAudience
import org.springframework.stereotype.Service

/** 공지 읽기 로직 보관함 — Facade에서만 호출된다. */
@Service
class NoticeReadService(
    private val noticeReadQueryPort: NoticeReadQueryPort,
) {
    /**
     * (고정 여부, 게시 시각, id) 내림차순 커서 페이지. 총건수는 첫 페이지에서만 센다 —
     * 뒤 페이지에서 매번 세면 조건 전체를 다시 훑는다.
     */
    fun findPage(
        reader: NoticeAudience,
        query: NoticeListQuery,
    ): CursorPageResult<NoticeReadView> {
        val anchor = query.cursor?.let(NoticeReadCursorCodec::decode)
        val condition =
            NoticeReadCondition(
                reader = reader,
                category = query.category,
                anchor = anchor,
                limit = CursorPageSize.fetchLimit(query.size),
            )
        val totalCount = if (query.cursor == null) noticeReadQueryPort.count(condition) else null
        return CursorPageResult.of(
            fetched = noticeReadQueryPort.findPage(condition),
            size = query.size,
            totalCount = totalCount,
        ) { last -> NoticeReadCursorCodec.encode(last.pinned, last.publishedAt, last.noticeId) }
    }

    /**
     * 공지 하나. 초안·내린 공지와 "다른 대상의 공지"를 같은 404로 끊는다 —
     * 코드를 갈라 주면 셀러가 구매자 공지의 존재를 확인하는 수단이 된다.
     */
    fun getVisible(
        reader: NoticeAudience,
        publicId: String,
    ): NoticeReadView =
        noticeReadQueryPort.findVisible(publicId, reader)
            ?: throw BusinessException(NoticeErrorCode.NOTICE_NOT_FOUND)
}
