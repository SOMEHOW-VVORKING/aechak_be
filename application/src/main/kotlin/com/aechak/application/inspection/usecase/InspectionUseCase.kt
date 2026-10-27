package com.aechak.application.inspection.usecase

import com.aechak.application.inspection.usecase.command.ApplyVerdictCommand

interface InspectionUseCase {
    /** 판정을 받아 적는다. 늦게 온 중복 판정이나 내용이 바뀐 뒤 도착한 판정은 버린다 */
    fun applyVerdict(command: ApplyVerdictCommand)

    /**
     * 기한이 지난 건을 닫는다.
     *
     * 대상은 **판정을 받지 못한 건만**이다. 명시적으로 보류 판정이 난 상품까지 풀어 주면
     * 기한만 지나면 통과하는 셈이 되어 검수가 장식이 된다.
     *
     * @return 닫은 건수
     */
    fun expireOverdue(): Int
}
