package com.aechak.domain.user.term.repository

import com.aechak.domain.user.term.ConsentRecord

interface ConsentRecordRepository {
    fun saveAll(records: List<ConsentRecord>): List<ConsentRecord>

    /** 유저의 해당 약관 동의 이력 전체(id 순) — 유효 동의(최신 행) 판정은 호출자가 한다. */
    fun findAllByUserAndTerms(
        userId: Long,
        termIds: Collection<Long>,
    ): List<ConsentRecord>
}
