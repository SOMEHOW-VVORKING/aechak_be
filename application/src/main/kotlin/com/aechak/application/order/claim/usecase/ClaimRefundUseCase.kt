package com.aechak.application.order.claim.usecase

import com.aechak.application.order.claim.usecase.command.ExecuteClaimRefundCommand
import com.aechak.application.order.claim.usecase.result.ExecuteClaimRefundResult
import com.aechak.application.order.claim.usecase.result.RefundPendingClaimResult

/**
 * 클레임 환불 집행 계약 — 배치만 소비한다. 구매자·셀러가 쓰는 계약과 메서드가 겹치지 않아 인터페이스를 갈랐고,
 * 셀러 자격 검증을 주입받지 않아 구현도 별도 파사드다.
 *
 * 스캔과 집행을 나눈 이유는 집행 안에 PG 호출이 있어 트랜잭션을 쪼개야 하기 때문이다 —
 * 스캔 결과를 트랜잭션에 걸어 둔 채로 외부 호출을 하면 커넥션이 PG 응답만큼 묶인다.
 */
interface ClaimRefundUseCase {
    /** 승인됐지만 환불이 집행되지 않은 클레임을 id 오름차순으로 limit건까지 */
    fun findRefundPendingClaims(limit: Int): List<RefundPendingClaimResult>

    /**
     * 환불 집행 — 환불 요청 상태로 바꾸는 트랜잭션 하나가 유일한 멱등 게이트다.
     * 재고·적립금 복원에는 멱등키가 없고(적립금은 클레임 단위 키가 있지만 재고에는 없다),
     * 그 선점 커밋에 성공한 호출만 PG를 부르고 자산을 되돌린다. 두 번 돌려도 자산은 한 번만 움직인다.
     */
    fun executeRefund(command: ExecuteClaimRefundCommand): ExecuteClaimRefundResult
}
