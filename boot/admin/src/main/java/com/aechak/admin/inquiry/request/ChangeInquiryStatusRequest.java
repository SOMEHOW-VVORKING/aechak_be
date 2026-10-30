package com.aechak.admin.inquiry.request;

import com.aechak.application.inquiry.usecase.command.ChangeInquiryStatusCommand;
import com.aechak.domain.inquiry.inquiry.enums.InquiryStatus;
import jakarta.validation.constraints.NotNull;

/** 상태 전이 요청 — 어떤 상태로 옮길 수 있는지는 문의 애그리거트가 판정한다(120009). */
public record ChangeInquiryStatusRequest(@NotNull InquiryStatus status) {

    public ChangeInquiryStatusCommand toCommand(long adminId, long inquiryId) {
        return new ChangeInquiryStatusCommand(adminId, inquiryId, status);
    }
}
