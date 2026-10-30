package com.aechak.application.notice.admin.port;

import com.aechak.application.notice.admin.port.view.AdminNoticeView;
import java.util.List;

/** 운영자용 공지 목록 조회 포트 — 작성자로 잠그지 않는 전역 조회다. */
public interface AdminNoticeQueryPort {

    List<AdminNoticeView> findPage(AdminNoticeCondition condition);

    /** 필터 조건의 총건수 — 커서 앵커는 적용하지 않는다(첫 페이지에서만 쓴다). */
    long count(AdminNoticeCondition condition);
}
