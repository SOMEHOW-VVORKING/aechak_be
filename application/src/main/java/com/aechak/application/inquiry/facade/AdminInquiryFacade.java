package com.aechak.application.inquiry.facade;

import com.aechak.application.inquiry.port.view.AdminInquiryView;
import com.aechak.application.inquiry.service.AdminInquiryService;
import com.aechak.application.inquiry.usecase.AdminInquiryUseCase;
import com.aechak.application.inquiry.usecase.command.AnswerInquiryCommand;
import com.aechak.application.inquiry.usecase.command.ChangeInquiryStatusCommand;
import com.aechak.application.inquiry.usecase.query.AdminInquirySearchQuery;
import com.aechak.application.inquiry.usecase.result.AdminInquiryAuthorResult;
import com.aechak.application.inquiry.usecase.result.AdminInquiryDetailResult;
import com.aechak.application.inquiry.usecase.result.AdminInquirySummaryResult;
import com.aechak.application.inquiry.usecase.result.PreviousInquiryResult;
import com.aechak.application.support.CursorPageResult;
import com.aechak.application.user.user.usecase.UserUseCase;
import com.aechak.application.user.user.usecase.result.UserAuthorResult;
import com.aechak.domain.inquiry.inquiry.Inquiry;
import com.aechak.domain.support.AggregateRoot;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * AdminInquiryUseCase의 유일한 구현체. @Transactional 경계는 여기 고정.
 *
 * <p>쓰기 경로에 외부 네트워크 호출이 없어 선언적 경계로 충분하다 — 답변 통지는 이 트랜잭션에서
 * 발송하지 않고 사실 이벤트(InquiryAnsweredEvent)만 남긴다.
 *
 * <p>작성자 표기는 user BC의 UseCase를 경유한다(탈퇴 계정 익명 처리 규칙이 그쪽 소유).
 * 목록은 페이지의 작성자를 한 번에 모아 조회한다 — 줄마다 부르면 N+1이 된다.
 */
@Service
@RequiredArgsConstructor
public class AdminInquiryFacade implements AdminInquiryUseCase {

    private final AdminInquiryService adminInquiryService;
    private final UserUseCase userUseCase;
    private final ApplicationEventPublisher eventPublisher;

    @Transactional(readOnly = true)
    @Override
    public CursorPageResult<AdminInquirySummaryResult> list(AdminInquirySearchQuery query) {
        CursorPageResult<AdminInquiryView> page = adminInquiryService.findPage(query);
        Map<Long, UserAuthorResult> authors = userUseCase.getAuthors(
                page.getItems().stream().map(AdminInquiryView::getUserId).distinct().toList());
        return new CursorPageResult<>(
                page.getItems().stream()
                        .map(view -> AdminInquirySummaryResult.from(view, authorOf(authors, view.getUserId())))
                        .toList(),
                page.getTotalCount(),
                page.getNextCursor(),
                page.getHasNext());
    }

    @Transactional(readOnly = true)
    @Override
    public AdminInquiryDetailResult detail(long inquiryId) {
        return toDetail(adminInquiryService.getById(inquiryId));
    }

    @Transactional
    @Override
    public AdminInquiryDetailResult answer(AnswerInquiryCommand command) {
        Inquiry inquiry = adminInquiryService.registerAnswer(command, LocalDateTime.now());
        publishEvents(inquiry);
        return toDetail(inquiry);
    }

    @Transactional
    @Override
    public AdminInquiryDetailResult changeStatus(ChangeInquiryStatusCommand command) {
        return toDetail(adminInquiryService.changeStatus(command));
    }

    private AdminInquiryDetailResult toDetail(Inquiry inquiry) {
        long userId = inquiry.getUserId();
        return AdminInquiryDetailResult.from(
                inquiry,
                authorOf(userUseCase.getAuthors(List.of(userId)), userId),
                adminInquiryService.previousInquiriesOf(inquiry).stream()
                        .map(PreviousInquiryResult::from)
                        .toList());
    }

    /** getAuthors는 요청한 id 전부에 대해 항목을 채워 돌려준다(없는 계정도 익명 표기로) — 비면 조립 전제가 깨진 것이다. */
    private AdminInquiryAuthorResult authorOf(Map<Long, UserAuthorResult> authors, long userId) {
        UserAuthorResult author = authors.get(userId);
        if (author == null) {
            throw new IllegalStateException("작성자 표기를 조립할 수 없습니다 (userId=" + userId + ")");
        }
        return AdminInquiryAuthorResult.from(author);
    }

    private void publishEvents(AggregateRoot aggregate) {
        aggregate.getEvents().forEach(eventPublisher::publishEvent);
        aggregate.clearEvents();
    }
}
