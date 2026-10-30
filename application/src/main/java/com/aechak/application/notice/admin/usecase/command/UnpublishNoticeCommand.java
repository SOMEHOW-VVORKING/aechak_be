package com.aechak.application.notice.admin.usecase.command;

/** 공지 내림 입력. */
public record UnpublishNoticeCommand(long adminId, String publicId) {}
