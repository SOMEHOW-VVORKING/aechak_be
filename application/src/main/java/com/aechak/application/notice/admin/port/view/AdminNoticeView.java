package com.aechak.application.notice.admin.port.view;

import com.aechak.domain.notice.notice.enums.NoticeAudience;
import com.aechak.domain.notice.notice.enums.NoticeCategory;
import com.aechak.domain.notice.notice.enums.NoticeStatus;
import java.time.LocalDateTime;

/**
 * 운영자용 공지 목록 읽기 모델.
 *
 * <p>본문과 내부 메모는 싣지 않는다 — 목록은 제목·분류·대상·상태만 보이고 전문은 상세에서 읽는다.
 * 내부 id(noticeId)는 커서 타이브레이크에만 쓰고 응답으로 내보내지 않는다.
 */
public record AdminNoticeView(
        long noticeId,
        String publicId,
        String title,
        NoticeCategory category,
        NoticeAudience audience,
        NoticeStatus status,
        boolean pinned,
        LocalDateTime publishedAt,
        Long lastModifiedByAdminId,
        LocalDateTime createdAt) {}
