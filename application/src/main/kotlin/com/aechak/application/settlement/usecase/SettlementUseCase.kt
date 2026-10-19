package com.aechak.application.settlement.usecase

import com.aechak.application.settlement.usecase.command.RegisterVerifiedAccountCommand

/** settlement BC 진입점 — 타 BC(셀러 심사 승인)는 이 UseCase로만 들어온다(리포지토리 직주입 금지). */
interface SettlementUseCase {
    /** 검증 완료(VERIFIED) 정산계좌 등록 — 사람이 통장사본을 대조한 승인 경로 전용. 호출자 트랜잭션에 참여한다. */
    fun registerVerifiedAccount(command: RegisterVerifiedAccountCommand)
}
