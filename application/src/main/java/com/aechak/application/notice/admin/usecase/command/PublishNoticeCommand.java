package com.aechak.application.notice.admin.usecase.command;

/** 공지 게시 입력. */
public record PublishNoticeCommand(long adminId, String publicId) {}
