package com.aechak.domain.settlement.settlement.repository

import com.aechak.domain.settlement.payout.Payout
import com.aechak.domain.settlement.settlement.Settlement
import java.time.LocalDate

interface SettlementRepository {
    fun saveAll(settlements: List<Settlement>): List<Settlement>

    /** 멱등키 단건 조회 — 같은 주문품목으로 원장을 두 번 만들지 않기 위한 선조회. */
    fun findByIdempotencyKey(idempotencyKey: String): Settlement?

    /** 주문 하나의 품목 키를 한 번에 조회한다 — 이미 원장이 있는 품목을 걸러내는 데 쓴다. */
    fun findAllByIdempotencyKeyIn(idempotencyKeys: List<String>): List<Settlement>

    /**
     * 기준일까지 지급 예정일이 도래한 지급 집행 후보 — 정산대기이고 아직 지급 묶음에 들어가지 않은 원장이다.
     * 환불 차감 제외는 행을 받은 뒤 [Settlement.isPayoutEligible]이 판정한다.
     */
    fun findAllPayoutCandidates(baseDate: LocalDate): List<Settlement>

    /** 셀러 하나의 지급 집행 후보 — 지급 건을 만드는 트랜잭션이 자기 몫만 다시 읽는다. */
    fun findAllPayoutCandidatesBySellerId(
        sellerId: Long,
        baseDate: LocalDate,
    ): List<Settlement>

    /**
     * 지급 대상 조건을 만족하는 원장만 지급 묶음에 연결한다 — 동시 집행의 선점 심판.
     * 연결된 행 수를 돌려주므로 요청보다 적으면 다른 실행이 그 사이에 같은 원장을 집어 간 것이다.
     */
    fun assignPayoutIfEligible(
        payout: Payout,
        settlementIds: List<Long>,
    ): Int
}
