package com.aechak.domain.settlement.settlement.repository

import com.aechak.domain.settlement.settlement.Settlement

interface SettlementRepository {
    fun saveAll(settlements: List<Settlement>): List<Settlement>

    /** 멱등키 단건 조회 — 같은 주문품목으로 원장을 두 번 만들지 않기 위한 선조회. */
    fun findByIdempotencyKey(idempotencyKey: String): Settlement?

    /** 주문 하나의 품목 키를 한 번에 조회한다 — 이미 원장이 있는 품목을 걸러내는 데 쓴다. */
    fun findAllByIdempotencyKeyIn(idempotencyKeys: List<String>): List<Settlement>
}
