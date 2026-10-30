package com.aechak.application.notice.admin.usecase.query;

import com.aechak.application.support.CursorPageSize;
import com.aechak.domain.notice.notice.enums.NoticeAudience;
import com.aechak.domain.notice.notice.enums.NoticeCategory;
import com.aechak.domain.notice.notice.enums.NoticeStatus;

/**
 * 운영자용 공지 목록 조회 입력. of()가 형식 오류를 보정한다 — size는 1~100 절삭,
 * 공백뿐인 keyword는 미적용(null). 커서는 그대로 넘겨 코덱이 판정한다(형식 오류는 90002).
 */
public record AdminNoticeSearchQuery(
        NoticeStatus status,
        NoticeAudience audience,
        NoticeCategory category,
        String keyword,
        String cursor,
        int size) {

    public static AdminNoticeSearchQuery of(
            NoticeStatus status,
            NoticeAudience audience,
            NoticeCategory category,
            String keyword,
            String cursor,
            int size) {
        String normalizedKeyword = keyword == null || keyword.isBlank() ? null : keyword.trim();
        int normalizedSize = (int) Math.min(Math.max(size, CursorPageSize.MIN), CursorPageSize.MAX);
        return new AdminNoticeSearchQuery(status, audience, category, normalizedKeyword, cursor, normalizedSize);
    }
}
