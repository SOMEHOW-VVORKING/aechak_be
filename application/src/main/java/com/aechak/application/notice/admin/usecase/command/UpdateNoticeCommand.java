package com.aechak.application.notice.admin.usecase.command;

import com.aechak.domain.notice.notice.enums.NoticeAudience;
import com.aechak.domain.notice.notice.enums.NoticeCategory;

/** 공지 내용 수정 입력. 게시 상태는 바꾸지 않는다 — 게시·내림은 각자의 경로다. */
public record UpdateNoticeCommand(
        long adminId,
        String publicId,
        String title,
        String content,
        NoticeCategory category,
        NoticeAudience audience,
        boolean pinned,
        String internalMemo) {}
