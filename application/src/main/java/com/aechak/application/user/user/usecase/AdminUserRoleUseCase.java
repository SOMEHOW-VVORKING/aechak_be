package com.aechak.application.user.user.usecase;

import com.aechak.application.support.PageQuery;
import com.aechak.application.support.PageResult;
import com.aechak.application.user.user.usecase.result.AdminUserRoleResult;
import java.util.List;

/**
 * 어드민 관리자 명부 — 자격(role=ADMIN)은 어드민 모듈 게이트가 보증한다.
 * 전역 자원 접근이라 소유권 검증 계층이 없다.
 */
public interface AdminUserRoleUseCase {

    /** 관리자 목록 — 가입 순(userId 오름차순). */
    PageResult<AdminUserRoleResult> listAdmins(PageQuery pageQuery);

    /** 승격 — 이미 관리자면 30012, 비활성 계정이면 불변식 위반(500). adminId는 감사 로그용. */
    void promote(long adminId, long targetUserId);

    /** 강등 — 이미 일반 회원이면 멱등 no-op. adminId는 감사 로그용. */
    void demote(long adminId, long targetUserId);

    /**
     * 기동 부트스트랩 — 설정 명부를 멱등 적용한다. 관리자가 0명이 된 상태의 복구 경로이기도 하다.
     * 대상 1건의 실패(미존재·비활성·이미 관리자)는 경고 로그만 남기고 나머지 대상을 계속 처리한다.
     */
    void ensureBootstrapAdmins(List<Long> userIds);
}
