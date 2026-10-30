package com.aechak.application.notice.admin.usecase.result;

import com.aechak.domain.notice.notice.Notice;
import com.aechak.domain.notice.notice.enums.NoticeAudience;
import com.aechak.domain.notice.notice.enums.NoticeCategory;
import com.aechak.domain.notice.notice.enums.NoticeStatus;
import java.time.LocalDateTime;

/**
 * 공지 상세 — 운영자만 본다. 내부 메모가 이 결과에만 실린다.
 *
 * <p>내부 id는 담지 않는다 — 외부 노출 식별자는 publicId뿐이다.
 */
public record AdminNoticeDetailResult(
        String publicId,
        String title,
        String content,
        NoticeCategory category,
        NoticeAudience audience,
        NoticeStatus status,
        boolean pinned,
        LocalDateTime publishedAt,
        String internalMemo,
        long createdByAdminId,
        Long lastModifiedByAdminId,
        LocalDateTime createdAt,
        LocalDateTime updatedAt) {

    public static AdminNoticeDetailResult from(Notice notice) {
        return new AdminNoticeDetailResult(
                notice.getPublicId(),
                notice.getTitle(),
                notice.getContent(),
                notice.getCategory(),
                notice.getAudience(),
                notice.getStatus(),
                notice.getPinned(),
                notice.getPublishedAt(),
                notice.getInternalMemo(),
                notice.getCreatedByAdminId(),
                notice.getLastModifiedByAdminId(),
                notice.getCreatedAt(),
                notice.getUpdatedAt());
    }
}
