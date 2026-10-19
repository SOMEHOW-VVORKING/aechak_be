package com.aechak.application.order.claim.usecase

import com.aechak.application.order.claim.usecase.command.AutoApproveClaimCommand
import com.aechak.application.order.claim.usecase.result.AutoApprovableClaimResult
import com.aechak.application.order.claim.usecase.result.AutoApproveClaimResult

/**
 * 셀러 미응답 클레임의 자동승인 계약 — 배치만 소비한다. 구매자·셀러가 쓰는 계약과 메서드가 겹치지 않아 인터페이스를 갈랐고,
 * 셀러 자격 검증을 주입받지 않아 구현도 별도 파사드다.
 *
 * 환불은 이 계약의 범위가 아니다 — 승인은 상태만 바꾸고, 집행은 환불 계약이 맡는다.
 */
interface ClaimAutoApproveUseCase {
    /** 응답 기한이 지난 셀러 미응답 클레임을 기한이 이른 순으로 limit건까지 */
    fun findAutoApprovableClaims(limit: Int): List<AutoApprovableClaimResult>

    /**
     * 자동승인 — 기한 재확인과 전이를 한 트랜잭션에서 한다.
     * 스캔과 승인 사이에 셀러가 먼저 처리했거나 구매자가 철회했으면 건너뛴다.
     */
    fun autoApprove(command: AutoApproveClaimCommand): AutoApproveClaimResult
}
