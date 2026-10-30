package com.aechak.application.notice.admin.usecase;

import com.aechak.application.notice.admin.usecase.command.CreateNoticeCommand;
import com.aechak.application.notice.admin.usecase.command.PublishNoticeCommand;
import com.aechak.application.notice.admin.usecase.command.UnpublishNoticeCommand;
import com.aechak.application.notice.admin.usecase.command.UpdateNoticeCommand;
import com.aechak.application.notice.admin.usecase.query.AdminNoticeSearchQuery;
import com.aechak.application.notice.admin.usecase.result.AdminNoticeDetailResult;
import com.aechak.application.notice.admin.usecase.result.AdminNoticeSummaryResult;
import com.aechak.application.support.CursorPageResult;

/**
 * 어드민 공지 작성·게시 — 자격(role=ADMIN)은 어드민 모듈 게이트가 보증한다.
 * 전역 자원 접근이라 소유권 검증 계층이 없다.
 *
 * <p>쓰기 네 경로가 상세를 돌려주는 이유: 운영 화면이 처리 직후 바뀐 상태·게시 시각을 그대로
 * 그려야 해서 재조회 왕복을 없앤다.
 *
 * <p>발송(푸시·메일)은 이 계약에 없다 — 어드민 실행 모듈에 발송 어댑터가 조립돼 있지 않다.
 * 게시는 공지를 읽을 수 있게 만드는 것까지이고, 밀어 보내는 일은 알림 도메인의 몫이다.
 */
public interface AdminNoticeUseCase {

    /** 공지 목록 — 최근 작성순. 총건수는 첫 페이지(cursor 없음)에서만 센다. */
    CursorPageResult<AdminNoticeSummaryResult> list(AdminNoticeSearchQuery query);

    AdminNoticeDetailResult detail(String publicId);

    /** 초안 생성. 게시는 별도 경로다. */
    AdminNoticeDetailResult create(CreateNoticeCommand command);

    /** 내용 수정. 게시 중 공지도 고칠 수 있고 상태는 그대로 남는다. */
    AdminNoticeDetailResult update(UpdateNoticeCommand command);

    /** 게시. 이미 게시 중이면 150006으로 거절한다. */
    AdminNoticeDetailResult publish(PublishNoticeCommand command);

    /** 내림. 게시 중이 아니면 150007로 거절한다. */
    AdminNoticeDetailResult unpublish(UnpublishNoticeCommand command);
}
