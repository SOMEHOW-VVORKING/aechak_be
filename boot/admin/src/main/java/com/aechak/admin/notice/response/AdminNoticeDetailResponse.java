package com.aechak.admin.notice.response;

import com.aechak.application.notice.admin.usecase.result.AdminNoticeDetailResult;
import com.aechak.domain.notice.notice.enums.NoticeAudience;
import com.aechak.domain.notice.notice.enums.NoticeCategory;
import com.aechak.domain.notice.notice.enums.NoticeStatus;
import java.time.LocalDateTime;

/**
 * 운영자용 공지 상세. 내부 메모가 이 응답에만 실린다.
 *
 * <p>발송 관련 필드(푸시 여부·도달 수)는 없다 — 어드민 실행 모듈에 발송 어댑터가 조립돼 있지 않아
 * 내려줄 값이 없고, 자리만 만들면 "보냈다"고 읽히는 계약이 된다.
 */
public record AdminNoticeDetailResponse(
        String noticeId,
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

    public static AdminNoticeDetailResponse from(AdminNoticeDetailResult result) {
        return new AdminNoticeDetailResponse(
                result.publicId(),
                result.title(),
                result.content(),
                result.category(),
                result.audience(),
                result.status(),
                result.pinned(),
                result.publishedAt(),
                result.internalMemo(),
                result.createdByAdminId(),
                result.lastModifiedByAdminId(),
                result.createdAt(),
                result.updatedAt());
    }
}
