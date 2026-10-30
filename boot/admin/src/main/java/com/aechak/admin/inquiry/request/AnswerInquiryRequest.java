package com.aechak.admin.inquiry.request;

import com.aechak.application.inquiry.usecase.command.AnswerInquiryCommand;
import jakarta.validation.constraints.NotNull;

/**
 * 답변 등록 요청.
 *
 * <p>공백 여부·길이 상한은 어노테이션으로 거울을 두지 않는다 — 답변 본문 규칙은 문의 애그리거트가 소유하고
 * 계약도 그쪽 코드(120006·120007)를 약속한다. 여기서 막는 것은 필드 누락뿐이다.
 */
public record AnswerInquiryRequest(@NotNull String answer) {

    public AnswerInquiryCommand toCommand(long adminId, long inquiryId) {
        return new AnswerInquiryCommand(adminId, inquiryId, answer);
    }
}
