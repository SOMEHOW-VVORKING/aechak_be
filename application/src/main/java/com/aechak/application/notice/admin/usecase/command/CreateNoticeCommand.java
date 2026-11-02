package com.aechak.application.notice.admin.usecase.command;

import com.aechak.domain.notice.notice.enums.NoticeAudience;
import com.aechak.domain.notice.notice.enums.NoticeCategory;

/** 공지 초안 생성 입력. 길이·공백 검증은 애그리거트 팩토리가 한다. */
public record CreateNoticeCommand(
        long adminId,
        String title,
        String content,
        NoticeCategory category,
        NoticeAudience audience,
        boolean pinned,
        String internalMemo) {}
