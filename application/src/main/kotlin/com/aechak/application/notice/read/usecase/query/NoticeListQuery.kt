package com.aechak.application.notice.read.usecase.query

import com.aechak.application.support.CursorPageSize
import com.aechak.domain.notice.notice.enums.NoticeCategory

/**
 * 독자용 공지 목록 조회 입력. of()가 형식 오류를 보정한다 — size는 1~100 절삭.
 * 커서는 그대로 넘겨 코덱이 판정한다(형식 오류는 90002).
 *
 * 독자 신분(reader)은 이 입력에 담지 않는다 — 컨트롤러가 경로로 결정해 UseCase 인자로 넘긴다.
 * 요청 파라미터로 받으면 셀러가 구매자 공지를 요구할 수 있다.
 */
data class NoticeListQuery(
    val category: NoticeCategory?,
    val cursor: String?,
    val size: Int,
) {
    companion object {
        fun of(
            category: NoticeCategory?,
            cursor: String?,
            size: Int,
        ): NoticeListQuery =
            NoticeListQuery(
                category = category,
                cursor = cursor,
                size = size.coerceIn(CursorPageSize.MIN.toInt(), CursorPageSize.MAX.toInt()),
            )
    }
}
