package com.aechak.application.inquiry.usecase.command;

/** 답변 등록 입력 — adminId는 답변자 기록용이라 호출부(인증 컨텍스트)가 채운다. */
public record AnswerInquiryCommand(long adminId, long inquiryId, String answer) {}
