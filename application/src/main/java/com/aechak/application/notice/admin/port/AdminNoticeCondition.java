package com.aechak.application.notice.admin.port;

import com.aechak.domain.notice.notice.enums.NoticeAudience;
import com.aechak.domain.notice.notice.enums.NoticeCategory;
import com.aechak.domain.notice.notice.enums.NoticeStatus;

/**
 * 운영자용 공지 목록 조회 조건.
 *
 * <ul>
 *   <li>status·audience·category·keyword가 null이면 해당 조건 미적용
 *   <li>keyword는 제목만 훑는다 — 운영 화면의 검색창이 "공지 제목 검색"이다
 *   <li>anchor가 있으면 그 지점 다음부터 — 정렬은 최근 작성순(created_at, id) 고정
 * </ul>
 */
public record AdminNoticeCondition(
        NoticeStatus status,
        NoticeAudience audience,
        NoticeCategory category,
        String keyword,
        AdminNoticeAnchor anchor,
        int limit) {

    public AdminNoticeCondition {
        if (limit <= 0) {
            throw new IllegalArgumentException("limit은 양수여야 합니다.");
        }
    }
}
