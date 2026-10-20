package com.aechak.application.settlement.usecase.command

import java.time.LocalDate

/**
 * 기준일(KST) 하나의 정산 집행 명세. 지급 예정일이 기준일까지 도래한 정산을 셀러별로 묶어 지급 건을 만든다.
 * 기준일을 호출자가 실어 보내므로 같은 날을 다시 돌리는 것도, 빠진 과거 기준일을 수동으로 돌리는 것도 같은 입구다.
 */
data class ExecuteSettlementPayoutCommand(
    val baseDate: LocalDate,
)
