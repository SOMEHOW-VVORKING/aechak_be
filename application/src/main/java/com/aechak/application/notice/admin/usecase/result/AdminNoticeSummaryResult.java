package com.aechak.application.notice.admin.usecase.result;

import com.aechak.application.notice.admin.port.view.AdminNoticeView;
import com.aechak.domain.notice.notice.enums.NoticeAudience;
import com.aechak.domain.notice.notice.enums.NoticeCategory;
import com.aechak.domain.notice.notice.enums.NoticeStatus;
import java.time.LocalDateTime;

/**
 * 목록 한 줄. 본문·내부 메모는 담지 않는다.
 *
 * @param publishedAt 가장 최근 게시 시각. 한 번도 게시되지 않았으면 null이다.
 */
public record AdminNoticeSummaryResult(
        String publicId,
        String title,
        NoticeCategory category,
        NoticeAudience audience,
        NoticeStatus status,
        boolean pinned,
        LocalDateTime publishedAt,
        LocalDateTime createdAt) {

    public static AdminNoticeSummaryResult from(AdminNoticeView view) {
        return new AdminNoticeSummaryResult(
                view.publicId(),
                view.title(),
                view.category(),
                view.audience(),
                view.status(),
                view.pinned(),
                view.publishedAt(),
                view.createdAt());
    }
}
