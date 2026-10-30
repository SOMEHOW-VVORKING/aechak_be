package com.aechak.admin.notice.response;

import com.aechak.application.notice.admin.usecase.result.AdminNoticeSummaryResult;
import com.aechak.domain.notice.notice.enums.NoticeAudience;
import com.aechak.domain.notice.notice.enums.NoticeCategory;
import com.aechak.domain.notice.notice.enums.NoticeStatus;
import java.time.LocalDateTime;

/** 운영 목록 한 줄. enum은 그대로 싣는다 — 문자열화하면 생성 문서에 허용값이 실리지 않는다. */
public record AdminNoticeSummaryResponse(
        String noticeId,
        String title,
        NoticeCategory category,
        NoticeAudience audience,
        NoticeStatus status,
        boolean pinned,
        LocalDateTime publishedAt,
        LocalDateTime createdAt) {

    public static AdminNoticeSummaryResponse from(AdminNoticeSummaryResult result) {
        return new AdminNoticeSummaryResponse(
                result.publicId(),
                result.title(),
                result.category(),
                result.audience(),
                result.status(),
                result.pinned(),
                result.publishedAt(),
                result.createdAt());
    }
}
