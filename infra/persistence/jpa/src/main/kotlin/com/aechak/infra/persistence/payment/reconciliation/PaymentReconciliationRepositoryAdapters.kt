package com.aechak.infra.persistence.payment.reconciliation

import com.aechak.domain.payment.reconciliation.PaymentReconciliationItem
import com.aechak.domain.payment.reconciliation.PaymentReconciliationRun
import com.aechak.domain.payment.reconciliation.ReconciliationResolution
import com.aechak.domain.payment.reconciliation.ReconciliationSource
import com.aechak.domain.payment.reconciliation.repository.PaymentReconciliationItemRepository
import com.aechak.domain.payment.reconciliation.repository.PaymentReconciliationRunRepository
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.findByIdOrNull
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository
import java.time.LocalDate
import java.time.LocalDateTime

interface PaymentReconciliationRunJpaRepository : JpaRepository<PaymentReconciliationRunJpaEntity, Long> {
    fun findBySourceAndBaseDate(
        source: ReconciliationSource,
        baseDate: LocalDate,
    ): PaymentReconciliationRunJpaEntity?

    @Query(
        "select max(r.baseDate) from PaymentReconciliationRunJpaEntity r " +
            "where r.source = :source and r.status = com.aechak.domain.payment.reconciliation.ReconciliationRunStatus.COMPLETED",
    )
    fun findLatestCompletedBaseDate(
        @Param("source") source: ReconciliationSource,
    ): LocalDate?

    @Query(
        "select r.baseDate from PaymentReconciliationRunJpaEntity r " +
            "where r.source = :source and r.baseDate between :from and :until " +
            "and r.status <> com.aechak.domain.payment.reconciliation.ReconciliationRunStatus.COMPLETED " +
            "order by r.baseDate",
    )
    fun findIncompleteBaseDates(
        @Param("source") source: ReconciliationSource,
        @Param("from") from: LocalDate,
        @Param("until") until: LocalDate,
    ): List<LocalDate>
}

interface PaymentReconciliationItemJpaRepository : JpaRepository<PaymentReconciliationItemJpaEntity, Long> {
    fun findByRunIdAndPaymentKey(
        runId: Long,
        paymentKey: String,
    ): PaymentReconciliationItemJpaEntity?

    fun findAllByRunIdAndResolution(
        runId: Long,
        resolution: ReconciliationResolution,
    ): List<PaymentReconciliationItemJpaEntity>

    fun findAllByRunId(runId: Long): List<PaymentReconciliationItemJpaEntity>

    @Query(
        "select i from PaymentReconciliationItemJpaEntity i " +
            "where i.source = :source and i.resolution in :open " +
            "and i.runId in (" +
            "  select r.id from PaymentReconciliationRunJpaEntity r " +
            "  where r.source = :source and r.baseDate < :beforeBaseDate " +
            "  and (r.status <> com.aechak.domain.payment.reconciliation.ReconciliationRunStatus.RUNNING or r.startedAt < :staleBefore)" +
            ") " +
            "order by i.id",
    )
    fun findOpenItemsForCarryOver(
        @Param("source") source: ReconciliationSource,
        @Param("beforeBaseDate") beforeBaseDate: LocalDate,
        @Param("staleBefore") staleBefore: LocalDateTime,
        @Param("open") open: Collection<ReconciliationResolution>,
    ): List<PaymentReconciliationItemJpaEntity>
}

@Repository
class PaymentReconciliationRunRepositoryAdapter(
    private val jpaRepository: PaymentReconciliationRunJpaRepository,
) : PaymentReconciliationRunRepository {
    /** 기존 id의 저장은 merge — 순수 모델이 들고 온 version과 DB가 다르면 낙관적 락 예외가 난다(이어받기 경쟁의 심판) */
    override fun save(run: PaymentReconciliationRun): PaymentReconciliationRun =
        PaymentReconciliationMapper.toDomain(jpaRepository.saveAndFlush(PaymentReconciliationMapper.toEntity(run)))

    override fun findBySourceAndBaseDate(
        source: ReconciliationSource,
        baseDate: LocalDate,
    ): PaymentReconciliationRun? = jpaRepository.findBySourceAndBaseDate(source, baseDate)?.let(PaymentReconciliationMapper::toDomain)

    override fun findById(id: Long): PaymentReconciliationRun? =
        jpaRepository.findByIdOrNull(id)?.let(PaymentReconciliationMapper::toDomain)

    override fun findLatestCompletedBaseDate(source: ReconciliationSource): LocalDate? = jpaRepository.findLatestCompletedBaseDate(source)

    override fun findIncompleteBaseDates(
        source: ReconciliationSource,
        from: LocalDate,
        until: LocalDate,
    ): List<LocalDate> = jpaRepository.findIncompleteBaseDates(source, from, until)
}

@Repository
class PaymentReconciliationItemRepositoryAdapter(
    private val jpaRepository: PaymentReconciliationItemJpaRepository,
) : PaymentReconciliationItemRepository {
    override fun save(item: PaymentReconciliationItem): PaymentReconciliationItem =
        PaymentReconciliationMapper.toDomain(jpaRepository.save(PaymentReconciliationMapper.toEntity(item)))

    override fun findByRunIdAndPaymentKey(
        runId: Long,
        paymentKey: String,
    ): PaymentReconciliationItem? = jpaRepository.findByRunIdAndPaymentKey(runId, paymentKey)?.let(PaymentReconciliationMapper::toDomain)

    override fun findAllByRunIdAndResolution(
        runId: Long,
        resolution: ReconciliationResolution,
    ): List<PaymentReconciliationItem> =
        jpaRepository.findAllByRunIdAndResolution(runId, resolution).map(PaymentReconciliationMapper::toDomain)

    override fun findAllByRunId(runId: Long): List<PaymentReconciliationItem> =
        jpaRepository.findAllByRunId(runId).map(PaymentReconciliationMapper::toDomain)

    override fun findOpenItemsForCarryOver(
        source: ReconciliationSource,
        beforeBaseDate: LocalDate,
        staleBefore: LocalDateTime,
    ): List<PaymentReconciliationItem> =
        jpaRepository
            .findOpenItemsForCarryOver(source, beforeBaseDate, staleBefore, ReconciliationResolution.entries.filter { it.isOpen })
            .map(PaymentReconciliationMapper::toDomain)

    override fun findById(id: Long): PaymentReconciliationItem? =
        jpaRepository.findByIdOrNull(id)?.let(PaymentReconciliationMapper::toDomain)
}
