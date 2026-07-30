package com.aechak.domain.user.term.repository

import com.aechak.domain.user.term.ConsentRecord

interface ConsentRecordRepository {
    fun saveAll(records: List<ConsentRecord>): List<ConsentRecord>
}
