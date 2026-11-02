package com.aechak.admin.notice.request;

import com.aechak.application.notice.admin.usecase.command.CreateNoticeCommand;
import com.aechak.application.notice.admin.usecase.command.UpdateNoticeCommand;
import com.aechak.domain.notice.notice.enums.NoticeAudience;
import com.aechak.domain.notice.notice.enums.NoticeCategory;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * 공지 작성·수정 요청. 생성과 수정이 같은 본문을 받으므로 한 타입으로 둔다.
 *
 * <p>길이 상한은 도메인 상수와 같은 값을 쓴다 — 형식 범위를 여기서 먼저 막고(400 + 검증 실패 코드),
 * 같은 판정을 애그리거트가 한 번 더 한다. 애그리거트 쪽 판정이 계약의 정본이고 여기는 방어선이다.
 *
 * <p>pinned는 Boolean(박스)으로 받아 @NotNull로 막는다 — boolean으로 두면 누락이 조용히 false가 된다.
 */
public record SaveNoticeRequest(
        @NotBlank @Size(max = 200) String title,
        @NotBlank @Size(max = 2000) String content,
        @NotNull NoticeCategory category,
        @NotNull NoticeAudience audience,
        @NotNull Boolean pinned,
        @Size(max = 300) String internalMemo) {

    public CreateNoticeCommand toCreateCommand(long adminId) {
        return new CreateNoticeCommand(adminId, title, content, category, audience, pinned, internalMemo);
    }

    public UpdateNoticeCommand toUpdateCommand(long adminId, String publicId) {
        return new UpdateNoticeCommand(adminId, publicId, title, content, category, audience, pinned, internalMemo);
    }
}
