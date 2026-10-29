package com.aechak.application.user.sanction.usecase

import com.aechak.application.support.CursorPageResult
import com.aechak.application.user.sanction.usecase.command.ReleaseSanctionCommand
import com.aechak.application.user.sanction.usecase.command.SanctionMemberCommand
import com.aechak.application.user.sanction.usecase.query.MemberSearchQuery
import com.aechak.application.user.sanction.usecase.result.MemberDetailResult
import com.aechak.application.user.sanction.usecase.result.MemberSummaryResult

/**
 * 어드민 일반 회원 관리 — 조회와 제재.
 * 자격(role=ADMIN)은 어드민 모듈 게이트가 보증하므로 소유권 검증 계층이 없다(관리자 명부와 같은 결).
 */
interface AdminMemberUseCase {
    /** 회원 목록 — 가입 역순. 상태 필터가 커서와 어긋나면 90002. */
    fun listMembers(query: MemberSearchQuery): CursorPageResult<MemberSummaryResult>

    /** 회원 상세 — 없으면 30000. */
    fun getMember(userId: Long): MemberDetailResult

    /**
     * 제재 적용 — 이력 한 행을 남기고 계정 상태를 바꾼다.
     * 자기 자신이면 30020, 관리자 계정이면 30021, 종단 상태(탈퇴·영구 정지)면 30003·30018.
     */
    fun sanction(command: SanctionMemberCommand)

    /** 제재 해제 — 걸려 있는 제재가 없으면 30019. */
    fun release(command: ReleaseSanctionCommand)
}
