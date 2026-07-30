package com.aechak.infra.persistence.user.term

import com.aechak.domain.user.term.ConsentRecord
import com.aechak.domain.user.term.repository.ConsentRecordRepository
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

interface ConsentRecordJpaRepository : JpaRepository<ConsentRecord, Long>

@Repository
class ConsentRecordRepositoryAdapter(
    private val jpaRepository: ConsentRecordJpaRepository,
) : ConsentRecordRepository {
    override fun saveAll(records: List<ConsentRecord>): List<ConsentRecord> = jpaRepository.saveAll(records)
}
