package com.aechak.application.notice.admin.facade;

import com.aechak.application.notice.admin.service.AdminNoticeService;
import com.aechak.application.notice.admin.usecase.AdminNoticeUseCase;
import com.aechak.application.notice.admin.usecase.command.CreateNoticeCommand;
import com.aechak.application.notice.admin.usecase.command.PublishNoticeCommand;
import com.aechak.application.notice.admin.usecase.command.UnpublishNoticeCommand;
import com.aechak.application.notice.admin.usecase.command.UpdateNoticeCommand;
import com.aechak.application.notice.admin.usecase.query.AdminNoticeSearchQuery;
import com.aechak.application.notice.admin.usecase.result.AdminNoticeDetailResult;
import com.aechak.application.notice.admin.usecase.result.AdminNoticeSummaryResult;
import com.aechak.application.support.CursorPageResult;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * AdminNoticeUseCase의 유일한 구현체. @Transactional 경계는 여기 고정.
 *
 * <p>쓰기 경로에 외부 네트워크 호출이 없어 선언적 경계로 충분하다 — 게시는 상태 전이뿐이고
 * 어디로도 발송하지 않는다. 공지가 애그리거트 이벤트를 등록하지 않는 것도 같은 이유다:
 * 받을 리스너가 없는 이벤트를 발행하면 "통지가 나간다"는 오해가 계약에 남는다.
 */
@Service
@RequiredArgsConstructor
public class AdminNoticeFacade implements AdminNoticeUseCase {

    private final AdminNoticeService adminNoticeService;

    @Transactional(readOnly = true)
    @Override
    public CursorPageResult<AdminNoticeSummaryResult> list(AdminNoticeSearchQuery query) {
        return adminNoticeService.findPage(query).map(AdminNoticeSummaryResult::from);
    }

    @Transactional(readOnly = true)
    @Override
    public AdminNoticeDetailResult detail(String publicId) {
        return AdminNoticeDetailResult.from(adminNoticeService.getByPublicId(publicId));
    }

    @Transactional
    @Override
    public AdminNoticeDetailResult create(CreateNoticeCommand command) {
        return AdminNoticeDetailResult.from(adminNoticeService.createDraft(command));
    }

    @Transactional
    @Override
    public AdminNoticeDetailResult update(UpdateNoticeCommand command) {
        return AdminNoticeDetailResult.from(adminNoticeService.update(command));
    }

    @Transactional
    @Override
    public AdminNoticeDetailResult publish(PublishNoticeCommand command) {
        return AdminNoticeDetailResult.from(adminNoticeService.publish(command, LocalDateTime.now()));
    }

    @Transactional
    @Override
    public AdminNoticeDetailResult unpublish(UnpublishNoticeCommand command) {
        return AdminNoticeDetailResult.from(adminNoticeService.unpublish(command));
    }
}
