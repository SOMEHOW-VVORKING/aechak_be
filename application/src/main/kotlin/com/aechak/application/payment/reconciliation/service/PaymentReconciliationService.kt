package com.aechak.application.payment.reconciliation.service

import com.aechak.application.payment.port.GatewayPaymentRecord
import com.aechak.application.payment.service.PaymentService
import com.aechak.common.error.BusinessException
import com.aechak.domain.payment.error.PaymentErrorCode
import com.aechak.domain.payment.reconciliation.GatewaySnapshot
import com.aechak.domain.payment.reconciliation.MismatchType
import com.aechak.domain.payment.reconciliation.OurLedgerSnapshot
import com.aechak.domain.payment.reconciliation.PaymentReconciliationItem
import com.aechak.domain.payment.reconciliation.PaymentReconciliationRun
import com.aechak.domain.payment.reconciliation.ReconciliationCounts
import com.aechak.domain.payment.reconciliation.ReconciliationResolution
import com.aechak.domain.payment.reconciliation.ReconciliationRunStatus
import com.aechak.domain.payment.reconciliation.ReconciliationSource
import com.aechak.domain.payment.reconciliation.repository.PaymentReconciliationItemRepository
import com.aechak.domain.payment.reconciliation.repository.PaymentReconciliationRunRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime

/**
 * 대사 회차의 단계별 로직 보관함 — 트랜잭션은 Facade가 연다.
 * 모든 쓰기 단계는 run을 다시 읽어 내 세대(attempt)인지 확인한다. 죽은 줄 알고 이어받은 뒤에 원래 실행이 깨어나
 * 늦은 판정을 덮어쓰지 못하게 하려는 것이다. 우리 결제·그룹 로딩은 결제 BC의 PaymentService를 거친다.
 */
@Service
class PaymentReconciliationService(
    private val runRepository: PaymentReconciliationRunRepository,
    private val itemRepository: PaymentReconciliationItemRepository,
    private val paymentService: PaymentService,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    data class Window(
        val from: LocalDateTime,
        val until: LocalDateTime,
    )

    sealed interface StartOutcome {
        data class Started(
            val run: PaymentReconciliationRun,
        ) : StartOutcome

        data class Skipped(
            val reason: String,
        ) : StartOutcome
    }

    /** 기준일 전날 00:00 ~ 다음 날 00:00. 전날을 품는 이유는 자정 직전 승인·웹훅 재전송 지연이 다음 날 창에서 다시 잡히게 하려는 것 */
    fun windowOf(baseDate: LocalDate): Window =
        Window(
            from = baseDate.minusDays(LOOKBACK_DAYS).atStartOfDay(),
            until = baseDate.plusDays(1).atStartOfDay(),
        )

    /**
     * 회차를 새로 열거나 이어받는다. 완료된 회차와 살아서 도는 회차는 손대지 않는다.
     * 저장에서 나는 UNIQUE·낙관적 락 충돌은 "다른 실행이 먼저 잡았다"는 뜻이라 Facade가 Skipped로 번역한다.
     */
    fun startOrResume(
        source: ReconciliationSource,
        baseDate: LocalDate,
        now: LocalDateTime,
    ): StartOutcome {
        val existing = runRepository.findBySourceAndBaseDate(source, baseDate)
        if (existing == null) {
            val window = windowOf(baseDate)
            return StartOutcome.Started(
                runRepository.save(PaymentReconciliationRun.start(source, baseDate, window.from, window.until, now)),
            )
        }
        return when {
            existing.status == ReconciliationRunStatus.COMPLETED -> StartOutcome.Skipped("이미 완료된 회차(runId=${existing.id})")
            existing.canBeTakenOver(now, STALE_AFTER) -> StartOutcome.Started(runRepository.save(existing.resume(now)))
            else -> StartOutcome.Skipped("다른 실행이 진행 중(runId=${existing.id}, attempt=${existing.attempt}, startedAt=${existing.startedAt})")
        }
    }

    /** 내 세대인지만 확인한다 — 외부 호출(확정 본체) 직전에 불러, 소유권을 잃은 실행이 PG를 건드리지 않게 한다 */
    fun assertOwnership(
        runId: Long,
        attempt: Int,
    ) {
        requireOwnership(runId, attempt)
    }

    /**
     * 결제 식별자 하나를 대조해 회차에 기록한다. 같은 회차에 이미 행이 있으면 덮어쓴다(UNIQUE).
     * 우리 결제·그룹은 같은 트랜잭션에서 읽히므로 한 스냅샷의 조합이다.
     * 자동 보정을 지원하지 않는 소스의 승인은 사람 확인으로 낮춘다 — 확정 본체는 포트원에만 묻기 때문이다.
     */
    fun recordComparison(
        runId: Long,
        attempt: Int,
        paymentKey: String,
        gateway: GatewayPaymentRecord?,
        autoConfirmable: Boolean,
    ): PaymentReconciliationItem {
        val run = requireOwnership(runId, attempt)
        val ledger = paymentService.findLedgerByPaymentId(paymentKey)
        val payment = ledger?.payment
        val group = ledger?.group
        val verdict = PaymentReconciliationClassifier.classify(payment, group, gateway)
        val resolution =
            if (verdict.resolution == ReconciliationResolution.AUTO_PENDING && !autoConfirmable) {
                ReconciliationResolution.MANUAL_REQUIRED
            } else {
                verdict.resolution
            }
        val ours =
            payment?.let {
                OurLedgerSnapshot(
                    paymentId = it.id,
                    orderGroupId = it.orderGroupId,
                    paymentStatus = it.status.name,
                    groupStatus = group?.status?.name,
                    groupFinalAmount = group?.finalPaymentAmount,
                    targetAmount = it.targetAmount,
                    paidAmount = it.realPaidAmount,
                    cancellableAmount = it.cancellableAmount,
                )
            }
        val gatewaySnapshot =
            gateway?.let {
                GatewaySnapshot(
                    status = it.status.name,
                    paidAmount = it.paidAmount,
                    cancelledAmount = it.cancelledAmount,
                    pgTxId = it.pgTxId,
                    statusChangedAt = it.statusChangedAt,
                )
            }
        val existing = itemRepository.findByRunIdAndPaymentKey(run.id, paymentKey)
        val item =
            existing?.recompare(ours, gatewaySnapshot, verdict.mismatchType, resolution)
                ?: PaymentReconciliationItem.compare(
                    run.id,
                    run.source,
                    paymentKey,
                    ours,
                    gatewaySnapshot,
                    verdict.mismatchType,
                    resolution,
                )
        return itemRepository.save(item)
    }

    /**
     * 이월 — 이전 회차에 열려 있던 같은 결제의 건을 닫는다. 이번 회차의 새 판정이 그 자리를 대신한다.
     * 저장 직전에 다시 읽어 아직 열려 있는 건만 닫는다(그새 다른 회차가 닫았으면 손대지 않는다).
     */
    fun supersede(
        runId: Long,
        attempt: Int,
        openItems: List<PaymentReconciliationItem>,
        now: LocalDateTime,
    ) {
        requireOwnership(runId, attempt)
        val eligibleRuns = HashMap<Long, Boolean>()
        openItems.forEach { stale ->
            val current = itemRepository.findById(stale.id) ?: return@forEach
            if (!current.resolution.isOpen) return@forEach
            // 후보로 뽑은 뒤 옛 회차가 되살아났을 수 있다(죽은 줄 알았는데 이어받음). 살아 있으면 그 회차의 판정이 최신이라 닫지 않는다
            val eligible =
                eligibleRuns.getOrPut(current.runId) {
                    val oldRun = runRepository.findByIdForUpdate(current.runId)
                    oldRun == null || oldRun.status != ReconciliationRunStatus.RUNNING || oldRun.isStale(now, STALE_AFTER)
                }
            if (eligible) itemRepository.save(current.markSuperseded(runId, now))
        }
    }

    /** 창 안에서 우리 쪽 상태가 바뀐 결제 식별자 — PG 목록이 놓친 "우리만 있는 건"의 후보 */
    fun findOurPaymentKeysChangedIn(
        from: LocalDateTime,
        until: LocalDateTime,
    ): List<String> = paymentService.findPaymentIdsUpdatedBetween(from, until)

    /** 이월 후보 — 기준일이 더 이른, 끝났거나 죽은 회차의 열린 건. 살아서 도는 다른 회차의 건은 건드리지 않는다 */
    fun findOpenItemsForCarryOver(
        run: PaymentReconciliationRun,
        now: LocalDateTime,
    ): List<PaymentReconciliationItem> = itemRepository.findOpenItemsForCarryOver(run.source, run.baseDate, now.minus(STALE_AFTER))

    fun findAutoPending(runId: Long): List<PaymentReconciliationItem> =
        itemRepository.findAllByRunIdAndResolution(runId, ReconciliationResolution.AUTO_PENDING)

    fun markAutoConfirmed(
        runId: Long,
        attempt: Int,
        item: PaymentReconciliationItem,
        resolvedType: MismatchType,
        now: LocalDateTime,
    ) {
        requireOwnership(runId, attempt)
        itemRepository.save(item.markAutoConfirmed(resolvedType, now))
    }

    fun markManualRequired(
        runId: Long,
        attempt: Int,
        item: PaymentReconciliationItem,
        mismatchType: MismatchType,
        reason: String,
        now: LocalDateTime,
    ) {
        requireOwnership(runId, attempt)
        itemRepository.save(item.markManualRequired(mismatchType, reason, now))
    }

    fun markFailed(
        runId: Long,
        attempt: Int,
        item: PaymentReconciliationItem,
        reason: String,
        now: LocalDateTime,
    ) {
        requireOwnership(runId, attempt)
        itemRepository.save(item.markFailed(reason, now))
    }

    /** 회차를 닫는다. 건수는 이번 회차에 실제로 남은 건을 세어 적는다(증분 카운터가 아니라 캐시된 요약) */
    fun complete(
        runId: Long,
        attempt: Int,
        fetched: Int,
        now: LocalDateTime,
    ): Pair<PaymentReconciliationRun, List<PaymentReconciliationItem>> {
        val run = requireOwnership(runId, attempt)
        val items = itemRepository.findAllByRunId(run.id)
        val counts = countsOf(fetched, items)
        val completed = runRepository.save(run.complete(counts, now))
        return completed to items.filter { it.resolution.isOpen }
    }

    fun fail(
        runId: Long,
        attempt: Int,
        fetched: Int,
        message: String,
        now: LocalDateTime,
    ): PaymentReconciliationRun? {
        val run = runRepository.findById(runId) ?: return null
        if (!run.isOwnedBy(attempt)) {
            log.warn("실패 기록 생략 — 회차 소유권을 잃음. runId={}, attempt={}, current={}", runId, attempt, run.attempt)
            return null
        }
        val items = itemRepository.findAllByRunId(run.id)
        return runRepository.save(run.fail(countsOf(fetched, items), message, now))
    }

    fun findLatestCompletedBaseDate(source: ReconciliationSource): LocalDate? = runRepository.findLatestCompletedBaseDate(source)

    fun findIncompleteBaseDates(
        source: ReconciliationSource,
        from: LocalDate,
        until: LocalDate,
    ): List<LocalDate> = runRepository.findIncompleteBaseDates(source, from, until)

    private fun countsOf(
        fetched: Int,
        items: List<PaymentReconciliationItem>,
    ): ReconciliationCounts =
        ReconciliationCounts(
            fetched = fetched,
            compared = items.size,
            matched = items.count { it.mismatchType == MismatchType.MATCHED },
            autoResolved = items.count { it.resolution == ReconciliationResolution.AUTO_CONFIRMED },
            manualRequired = items.count { it.resolution == ReconciliationResolution.MANUAL_REQUIRED },
            // 보정을 못 끝낸 건은 전부 실패로 센다 — AUTO_PENDING이 남았다는 건 회차가 중간에 끊겼다는 뜻
            failed =
                items.count {
                    it.resolution == ReconciliationResolution.FAILED ||
                        it.resolution == ReconciliationResolution.AUTO_PENDING
                },
        )

    /**
     * run 행을 잠그고 내 세대인지 확인한다. 잠금은 호출한 트랜잭션이 끝날 때까지 유지되므로 그 안의 건 쓰기는
     * 다른 실행의 이어받기와 직렬화된다 — 검사와 쓰기 사이에 소유권이 바뀌는 창이 없다.
     */
    private fun requireOwnership(
        runId: Long,
        attempt: Int,
    ): PaymentReconciliationRun {
        val run = runRepository.findByIdForUpdate(runId) ?: throw BusinessException(PaymentErrorCode.RECONCILIATION_RUN_TAKEN_OVER)
        if (!run.isOwnedBy(attempt)) throw BusinessException(PaymentErrorCode.RECONCILIATION_RUN_TAKEN_OVER)
        return run
    }

    companion object {
        const val LOOKBACK_DAYS = 1L

        /** 이보다 오래 RUNNING이면 인스턴스가 죽은 것으로 본다. 정상 회차는 수 분 안에 끝난다 */
        val STALE_AFTER: Duration = Duration.ofHours(2)
    }
}
