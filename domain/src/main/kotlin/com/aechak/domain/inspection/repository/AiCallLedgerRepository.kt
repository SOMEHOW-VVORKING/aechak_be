package com.aechak.domain.inspection.repository

import com.aechak.domain.inspection.AiCallLedgerEntry

interface AiCallLedgerRepository {
    /**
     * 호출 기록을 적는다. 같은 `callId`가 이미 있으면 아무것도 하지 않는다.
     *
     * 판정이 다시 전달될 수 있어서 멱등해야 한다. 실패를 예외로 올리면 판정 적용 전체가
     * 롤백되는데, 원장은 회계 기록이지 판정의 전제가 아니다.
     *
     * @return 실제로 새로 적은 건수
     */
    fun insertIgnoringDuplicates(entries: List<AiCallLedgerEntry>): Int
}
