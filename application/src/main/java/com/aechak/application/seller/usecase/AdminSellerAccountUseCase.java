package com.aechak.application.seller.usecase;

import com.aechak.application.seller.usecase.command.ReleaseSellerSuspensionCommand;
import com.aechak.application.seller.usecase.command.SaveSellerMemoCommand;
import com.aechak.application.seller.usecase.command.SuspendSellerCommand;
import com.aechak.application.seller.usecase.query.AdminSellerSearchQuery;
import com.aechak.application.seller.usecase.result.AdminSellerDetailResult;
import com.aechak.application.seller.usecase.result.AdminSellerSummaryResult;
import com.aechak.application.support.PageResult;

/**
 * 어드민 셀러 계정 관리 — 승인된 셀러의 조회와 판매정지.
 * 자격(role=ADMIN)은 어드민 모듈 게이트가 보증하므로 소유권 검증 계층이 없다(심사·회원 관리와 같은 결).
 *
 * <p>여기서 다루는 것은 <b>판매 차단</b>이다. 계정 로그인 차단(users.status)은 /admin/members의 몫이고
 * 둘은 다른 층이라 섞지 않는다 — 셀러도 role=GENERAL이라 한 계정이 양쪽에 등장할 수 있다.
 */
public interface AdminSellerAccountUseCase {

    /** 셀러 목록 — 셀러 식별자 내림차순. 상태·유형·상호명으로 거른다. */
    PageResult<AdminSellerSummaryResult> list(AdminSellerSearchQuery query);

    /** 셀러 상세 — 현재 정지 사유·메모·제재 이력. 셀러가 아니면 10000. */
    AdminSellerDetailResult detail(long sellerId);

    /**
     * 판매정지 — 이력 한 행을 남기고 셀러 상태를 SUSPENDED로 옮긴다.
     * 이미 정지면 10007, 탈퇴·탈퇴 요청이면 10009, 동시 조치 충돌도 10009.
     */
    void suspend(SuspendSellerCommand command);

    /** 판매정지 해제 — 정지 직전 상태로 되돌린다. 정지 상태가 아니면 10008. */
    void releaseSuspension(ReleaseSellerSuspensionCommand command);

    /** 내부 메모 저장 — 제재 없이 메모만 남기는 경로다. */
    void saveMemo(SaveSellerMemoCommand command);
}
