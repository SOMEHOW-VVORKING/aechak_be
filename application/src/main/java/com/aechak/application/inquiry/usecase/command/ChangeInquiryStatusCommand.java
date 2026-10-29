package com.aechak.application.inquiry.usecase.command;

import com.aechak.domain.inquiry.inquiry.enums.InquiryStatus;

/** 상태 전이 입력 — 허용 대상 상태 판정은 도메인(Inquiry.changeStatus) 소관이다. */
public record ChangeInquiryStatusCommand(long adminId, long inquiryId, InquiryStatus targetStatus) {}
