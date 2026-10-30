package com.aechak.application.notice.admin.service;

import com.aechak.application.notice.admin.port.AdminNoticeAnchor;
import com.aechak.application.notice.admin.port.AdminNoticeCondition;
import com.aechak.application.notice.admin.port.AdminNoticeQueryPort;
import com.aechak.application.notice.admin.port.view.AdminNoticeView;
import com.aechak.application.notice.admin.support.AdminNoticeCursorCodec;
import com.aechak.application.notice.admin.usecase.command.CreateNoticeCommand;
import com.aechak.application.notice.admin.usecase.command.PublishNoticeCommand;
import com.aechak.application.notice.admin.usecase.command.UnpublishNoticeCommand;
import com.aechak.application.notice.admin.usecase.command.UpdateNoticeCommand;
import com.aechak.application.notice.admin.usecase.query.AdminNoticeSearchQuery;
import com.aechak.application.support.CursorPageResult;
import com.aechak.application.support.CursorPageSize;
import com.aechak.common.error.BusinessException;
import com.aechak.domain.notice.error.NoticeErrorCode;
import com.aechak.domain.notice.notice.Notice;
import com.aechak.domain.notice.notice.repository.NoticeRepository;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/** 어드민 공지 작성·게시 로직 보관함 — Facade에서만 호출된다. 전역 자원 접근이라 소유권 검증이 없다. */
@Service
@RequiredArgsConstructor
public class AdminNoticeService {

    private final NoticeRepository noticeRepository;
    private final AdminNoticeQueryPort adminNoticeQueryPort;

    public Notice getByPublicId(String publicId) {
        return found(noticeRepository.findByPublicId(publicId));
    }

    /**
     * 쓰기 경로 전용 조회 — 행을 잠가 읽는다. 잠그지 않으면 두 운영자가 같은 초안을 함께 읽고
     * 각자 게시 판정을 통과해, 늦게 커밋한 쪽이 앞선 게시 시각·수정자를 덮어쓴다
     * (게시 중복 판정 150006이 둘 다 통과한다).
     */
    public Notice getByPublicIdForUpdate(String publicId) {
        return found(noticeRepository.findByPublicIdForUpdate(publicId));
    }

    private Notice found(Notice notice) {
        if (notice == null) {
            throw new BusinessException(NoticeErrorCode.NOTICE_NOT_FOUND, null, null);
        }
        return notice;
    }

    /**
     * 최근 작성순 커서 페이지. 총건수는 첫 페이지에서만 센다 — 뒤 페이지에서 매번 세면 필터 전체를 다시 훑는다.
     * 커서 앵커는 목록 조건에만 걸고 카운트 조건에는 걸지 않는다(총건수는 페이지와 무관한 값).
     */
    public CursorPageResult<AdminNoticeView> findPage(AdminNoticeSearchQuery query) {
        AdminNoticeAnchor anchor = query.cursor() == null ? null : AdminNoticeCursorCodec.decode(query.cursor());
        AdminNoticeCondition condition = new AdminNoticeCondition(
                query.status(),
                query.audience(),
                query.category(),
                query.keyword(),
                anchor,
                CursorPageSize.INSTANCE.fetchLimit(query.size()));
        Long totalCount = query.cursor() == null ? adminNoticeQueryPort.count(condition) : null;
        return CursorPageResult.Companion.of(
                adminNoticeQueryPort.findPage(condition),
                query.size(),
                totalCount,
                last -> AdminNoticeCursorCodec.encode(last.createdAt(), last.noticeId()));
    }

    /** 초안 생성 — 검증은 애그리거트 팩토리가 한다. */
    public Notice createDraft(CreateNoticeCommand command) {
        return noticeRepository.save(Notice.Companion.draft(
                command.adminId(),
                command.title(),
                command.content(),
                command.category(),
                command.audience(),
                command.pinned(),
                command.internalMemo()));
    }

    public Notice update(UpdateNoticeCommand command) {
        Notice notice = getByPublicIdForUpdate(command.publicId());
        notice.edit(
                command.adminId(),
                command.title(),
                command.content(),
                command.category(),
                command.audience(),
                command.pinned(),
                command.internalMemo());
        return notice;
    }

    public Notice publish(PublishNoticeCommand command, LocalDateTime publishedAt) {
        Notice notice = getByPublicIdForUpdate(command.publicId());
        notice.publish(command.adminId(), publishedAt);
        return notice;
    }

    public Notice unpublish(UnpublishNoticeCommand command) {
        Notice notice = getByPublicIdForUpdate(command.publicId());
        notice.unpublish(command.adminId());
        return notice;
    }
}
