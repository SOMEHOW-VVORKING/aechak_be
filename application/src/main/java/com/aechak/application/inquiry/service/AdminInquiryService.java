package com.aechak.application.inquiry.service;

import com.aechak.application.inquiry.port.AdminInquiryAnchor;
import com.aechak.application.inquiry.port.AdminInquiryCondition;
import com.aechak.application.inquiry.port.AdminInquiryQueryPort;
import com.aechak.application.inquiry.port.view.AdminInquiryView;
import com.aechak.application.inquiry.port.view.PreviousInquiryView;
import com.aechak.application.inquiry.support.AdminInquiryCursorCodec;
import com.aechak.application.inquiry.usecase.command.AnswerInquiryCommand;
import com.aechak.application.inquiry.usecase.command.ChangeInquiryStatusCommand;
import com.aechak.application.inquiry.usecase.query.AdminInquirySearchQuery;
import com.aechak.application.support.CursorPageResult;
import com.aechak.application.support.CursorPageSize;
import com.aechak.common.error.BusinessException;
import com.aechak.domain.inquiry.error.InquiryErrorCode;
import com.aechak.domain.inquiry.inquiry.Inquiry;
import com.aechak.domain.inquiry.inquiry.repository.InquiryRepository;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/** 어드민 문의 처리 로직 보관함 — Facade에서만 호출된다. 전역 자원 접근이라 소유권 검증이 없다. */
@Service
@RequiredArgsConstructor
public class AdminInquiryService {

    /** 상세에 함께 싣는 과거 문의 건수 — 반복 문의 판단에 최근 몇 건이면 충분하다. */
    public static final int PREVIOUS_INQUIRY_LIMIT = 5;

    private final InquiryRepository inquiryRepository;
    private final AdminInquiryQueryPort adminInquiryQueryPort;

    public Inquiry getById(long inquiryId) {
        Inquiry inquiry = inquiryRepository.findById(inquiryId);
        if (inquiry == null) {
            throw new BusinessException(InquiryErrorCode.INQUIRY_NOT_FOUND, null, null);
        }
        return inquiry;
    }

    /**
     * 접수 오래된 순 커서 페이지. 총건수는 첫 페이지에서만 센다 — 뒤 페이지에서 매번 세면 필터 전체를 다시 훑는다.
     * 커서 앵커는 목록 조건에만 걸고 카운트 조건에는 걸지 않는다(총건수는 페이지와 무관한 값).
     */
    public CursorPageResult<AdminInquiryView> findPage(AdminInquirySearchQuery query) {
        AdminInquiryAnchor anchor =
                query.cursor() == null ? null : AdminInquiryCursorCodec.decode(query.cursor());
        AdminInquiryCondition condition = new AdminInquiryCondition(
                query.status(),
                query.inquiryType(),
                query.keyword(),
                anchor,
                CursorPageSize.INSTANCE.fetchLimit(query.size()));
        Long totalCount = query.cursor() == null ? adminInquiryQueryPort.count(condition) : null;
        return CursorPageResult.Companion.of(
                adminInquiryQueryPort.findPage(condition),
                query.size(),
                totalCount,
                last -> AdminInquiryCursorCodec.encode(last.getCreatedAt(), last.getInquiryId()));
    }

    /** 같은 작성자의 과거 문의 — 자기 행은 빼고 최신순 최대 {@value #PREVIOUS_INQUIRY_LIMIT}건. */
    public List<PreviousInquiryView> previousInquiriesOf(Inquiry inquiry) {
        return adminInquiryQueryPort.findPreviousByUserId(
                inquiry.getUserId(), inquiry.getId(), PREVIOUS_INQUIRY_LIMIT);
    }

    /** 답변 등록 — 전이·답변자 기록은 애그리거트가 판정한다. 반환한 애그리거트가 이벤트를 들고 있다. */
    public Inquiry registerAnswer(AnswerInquiryCommand command, LocalDateTime answeredAt) {
        Inquiry inquiry = getById(command.inquiryId());
        inquiry.registerAnswer(command.adminId(), command.answer(), answeredAt);
        return inquiry;
    }

    public Inquiry changeStatus(ChangeInquiryStatusCommand command) {
        Inquiry inquiry = getById(command.inquiryId());
        inquiry.changeStatus(command.adminId(), command.targetStatus());
        return inquiry;
    }
}
