package com.aechak.application.settlement.usecase

import com.aechak.application.settlement.usecase.command.ExecuteSettlementPayoutCommand
import com.aechak.application.settlement.usecase.result.SettlementPayoutExecutionResult

/**
 * 정산 집행(지급) 진입점 — 배치만 소비한다. 원장 생성·계좌 등록(SettlementUseCase)과 입구를 나눠
 * 배치가 구매확정 쪽 의존을 끌고 오지 않게 한다.
 *
 * 멱등: 같은 기준일로 두 번 돌려도 지급 건은 늘지 않는다. 기준일 UNIQUE가 배치를 하나로 묶고,
 * 지급 건은 `PAYOUT:{배치 id}:{셀러 id}` 송금키 UNIQUE가 하나로 묶는다.
 */
interface SettlementPayoutUseCase {
    fun executePayout(command: ExecuteSettlementPayoutCommand): SettlementPayoutExecutionResult
}
