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
        return found(inquiryRepository.findById(inquiryId));
    }

    /**
     * 쓰기 경로 전용 조회 — 행을 잠가 읽는다. 잠그지 않으면 두 운영자가 같은 RECEIVED 문의를 함께 읽고
     * 각자 판정을 통과해, 늦게 커밋한 쪽이 앞선 답변·답변자·완료시각을 덮어쓴다(상태 전이는 답변을 null로 되돌린다).
     */
    public Inquiry getByIdForUpdate(long inquiryId) {
        return found(inquiryRepository.findByIdForUpdate(inquiryId));
    }

    private Inquiry found(Inquiry inquiry) {
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

    /**
     * 같은 작성자가 이 문의보다 <b>먼저</b> 접수한 문의 — 최신순 최대 {@value #PREVIOUS_INQUIRY_LIMIT}건.
     * 기준을 "자기 행 제외"가 아니라 "접수 시점 이전"으로 둔다 — 제외만 하면 나중에 들어온 문의가
     * 과거 이력 자리를 차지해, 정작 앞선 문의가 상한에 밀려 안 보인다.
     */
    public List<PreviousInquiryView> previousInquiriesOf(Inquiry inquiry) {
        return adminInquiryQueryPort.findPreviousByUserId(
                inquiry.getUserId(), inquiry.getCreatedAt(), inquiry.getId(), PREVIOUS_INQUIRY_LIMIT);
    }

    /** 답변 등록 — 전이·답변자 기록은 애그리거트가 판정한다. 반환한 애그리거트가 이벤트를 들고 있다. */
    public Inquiry registerAnswer(AnswerInquiryCommand command, LocalDateTime answeredAt) {
        Inquiry inquiry = getByIdForUpdate(command.inquiryId());
        inquiry.registerAnswer(command.adminId(), command.answer(), answeredAt);
        return inquiry;
    }

    public Inquiry changeStatus(ChangeInquiryStatusCommand command) {
        Inquiry inquiry = getByIdForUpdate(command.inquiryId());
        inquiry.changeStatus(command.adminId(), command.targetStatus());
        return inquiry;
    }
}
