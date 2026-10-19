package com.aechak.application.settlement.usecase

import com.aechak.application.settlement.usecase.command.CreateSettlementLedgerCommand
import com.aechak.application.settlement.usecase.command.RegisterVerifiedAccountCommand

/** settlement BC 진입점 — 타 BC(셀러 심사 승인, 구매확정)는 이 UseCase로만 들어온다(리포지토리 직주입 금지). */
interface SettlementUseCase {
    /** 검증 완료(VERIFIED) 정산계좌 등록 — 사람이 통장사본을 대조한 승인 경로 전용. 호출자 트랜잭션에 참여한다. */
    fun registerVerifiedAccount(command: RegisterVerifiedAccountCommand)

    /**
     * 구매확정된 주문의 정산 원장을 품목마다 만든다. 호출자(구매확정) 트랜잭션에 참여한다 —
     * 원장 생성이 실패하면 구매확정도 함께 롤백된다.
     * 멱등: 같은 주문품목으로 다시 불려도 원장은 늘지 않는다.
     *
     * 전제 — 호출자는 그 주문의 구매확정 전이를 선점한 쪽이어야 한다. 선점 없이 같은 주문으로 동시에
     * 들어오면 둘 다 "원장 없음"을 보고 INSERT해 한쪽이 멱등키 UNIQUE에 걸려 트랜잭션째로 롤백된다.
     * 순차 재실행은 이 메서드가 접어 주지만 동시 호출은 접지 못한다.
     */
    fun createLedgerOnConfirm(command: CreateSettlementLedgerCommand)
}
