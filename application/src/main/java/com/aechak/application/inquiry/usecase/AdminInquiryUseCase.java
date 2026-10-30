package com.aechak.application.inquiry.usecase;

import com.aechak.application.inquiry.usecase.command.AnswerInquiryCommand;
import com.aechak.application.inquiry.usecase.command.ChangeInquiryStatusCommand;
import com.aechak.application.inquiry.usecase.query.AdminInquirySearchQuery;
import com.aechak.application.inquiry.usecase.result.AdminInquiryDetailResult;
import com.aechak.application.inquiry.usecase.result.AdminInquirySummaryResult;
import com.aechak.application.support.CursorPageResult;

/**
 * 어드민 서비스 문의 처리 — 자격(role=ADMIN)은 어드민 모듈 게이트가 보증한다.
 * 전역 자원 접근이라 소유권 검증 계층이 없다(접수측 InquiryUseCase와 다른 점).
 *
 * <p>쓰기 두 경로가 상세를 돌려주는 이유: 운영 화면이 처리 직후 바뀐 상태·답변자를 그대로 그려야 해서
 * 재조회 왕복을 없앤다.
 */
public interface AdminInquiryUseCase {

    /** 문의 목록 — 접수 오래된 순. 총건수는 첫 페이지(cursor 없음)에서만 센다. */
    CursorPageResult<AdminInquirySummaryResult> list(AdminInquirySearchQuery query);

    AdminInquiryDetailResult detail(long inquiryId);

    /** 답변 등록 + 완료 전이. 답변은 1회만 — 이미 완료된 문의는 120008로 거절한다. */
    AdminInquiryDetailResult answer(AnswerInquiryCommand command);

    /** 처리중·보류 전이. 답변 없이도 가능하고, 완료로 가는 경로는 answer뿐이다. */
    AdminInquiryDetailResult changeStatus(ChangeInquiryStatusCommand command);
}
