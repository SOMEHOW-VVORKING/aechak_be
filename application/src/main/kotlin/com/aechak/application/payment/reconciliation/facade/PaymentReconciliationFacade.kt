package com.aechak.application.payment.reconciliation.facade

import com.aechak.application.payment.port.GatewayPaymentRecord
import com.aechak.application.payment.port.PaymentReconciliationAlertPort
import com.aechak.application.payment.port.PaymentReconciliationSourcePort
import com.aechak.application.payment.reconciliation.service.PaymentReconciliationService
import com.aechak.application.payment.reconciliation.service.PaymentReconciliationService.StartOutcome
import com.aechak.application.payment.reconciliation.usecase.PaymentReconciliationUseCase
import com.aechak.application.payment.reconciliation.usecase.command.CatchUpReconciliationCommand
import com.aechak.application.payment.reconciliation.usecase.command.ReconcilePaymentsCommand
import com.aechak.application.payment.reconciliation.usecase.result.ReconcileOutcome
import com.aechak.application.payment.reconciliation.usecase.result.ReconcilePaymentsResult
import com.aechak.application.payment.usecase.PaymentUseCase
import com.aechak.application.payment.usecase.command.CompletePaymentByGatewayCommand
import com.aechak.application.payment.usecase.result.CompletePaymentStatus
import com.aechak.common.error.BusinessException
import com.aechak.domain.payment.error.PaymentErrorCode
import com.aechak.domain.payment.reconciliation.MismatchType
import com.aechak.domain.payment.reconciliation.PaymentReconciliationItem
import com.aechak.domain.payment.reconciliation.PaymentReconciliationRun
import com.aechak.domain.payment.reconciliation.ReconciliationResolution
import com.aechak.domain.payment.reconciliation.ReconciliationSource
import org.slf4j.LoggerFactory
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.dao.OptimisticLockingFailureException
import org.springframework.stereotype.Service
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.support.TransactionTemplate
import java.time.LocalDate
import java.time.LocalDateTime

/**
 * 결제 대사 유스케이스 구현. 결제 BC의 두 번째 파사드다 — 확정 흐름(PaymentFacade)과 입구를 분리해
 * 배치가 확정 파사드의 나머지 의존(웹훅 검증기 등)을 끌고 오지 않게 한다.
 *
 * 트랜잭션은 단계마다 짧게 연다. PG 호출(목록·단건)과 확정 본체 호출은 전부 트랜잭션 밖이다.
 * 자동 보정은 새 로직이 아니라 PaymentUseCase의 확정 본체(재조회·3중 대조·선점)를 태운다.
 */
@Service
class PaymentReconciliationFacade(
    private val service: PaymentReconciliationService,
    private val sources: List<PaymentReconciliationSourcePort>,
    private val paymentUseCase: PaymentUseCase,
    private val alert: PaymentReconciliationAlertPort,
    transactionManager: PlatformTransactionManager,
) : PaymentReconciliationUseCase {
    private val log = LoggerFactory.getLogger(javaClass)
    private val tx = TransactionTemplate(transactionManager)

    override fun reconcile(command: ReconcilePaymentsCommand): ReconcilePaymentsResult {
        val source =
            sources.find { it.source == command.source } ?: throw BusinessException(PaymentErrorCode.RECONCILIATION_SOURCE_UNAVAILABLE)
        val run =
            when (val outcome = start(command.source, command.baseDate)) {
                is StartOutcome.Skipped -> {
                    log.info("결제 대사 건너뜀. source={}, baseDate={}, reason={}", command.source, command.baseDate, outcome.reason)
                    return ReconcilePaymentsResult.skipped(command.source, command.baseDate, outcome.reason)
                }

                is StartOutcome.Started -> {
                    outcome.run
                }
            }
        return runGuarded(run, source)
    }

    /**
     * 빠진 날을 메운다 — 마지막 완료일 다음 날부터 untilBaseDate까지, 그리고 그 기간 안에서 완료되지 못한(실패·죽은) 날.
     * 처음 도는 환경이면 과거를 메울 이유가 없어 untilBaseDate 하루만 돈다.
     */
    override fun catchUp(command: CatchUpReconciliationCommand): List<ReconcilePaymentsResult> {
        val earliestAllowed = command.untilBaseDate.minusDays((command.maxDays - 1).toLong())
        val lastCompleted = tx.execute { service.findLatestCompletedBaseDate(command.source) }
        val incomplete = tx.execute { service.findIncompleteBaseDates(command.source, earliestAllowed, command.untilBaseDate) }!!
        val fromLastCompleted =
            when {
                lastCompleted == null -> {
                    listOf(command.untilBaseDate)
                }

                else -> {
                    val start = maxOf(lastCompleted.plusDays(1), earliestAllowed)
                    generateSequence(start) { it.plusDays(1) }.takeWhile { !it.isAfter(command.untilBaseDate) }.toList()
                }
            }
        val dates = (fromLastCompleted + incomplete).distinct().sorted()
        val results = mutableListOf<ReconcilePaymentsResult>()
        for (date in dates) {
            val result = reconcile(ReconcilePaymentsCommand(command.source, date))
            results += result
            if (result.outcome == ReconcileOutcome.FAILED) break
        }
        return results
    }

    private fun start(
        source: ReconciliationSource,
        baseDate: LocalDate,
    ): StartOutcome =
        try {
            tx.execute { service.startOrResume(source, baseDate, LocalDateTime.now()) }!!
        } catch (e: DataIntegrityViolationException) {
            // 같은 (소스, 기준일)을 두 실행이 동시에 열었다 — UNIQUE가 심판이고 진 쪽은 물러난다
            StartOutcome.Skipped("다른 실행이 같은 회차를 먼저 열었음")
        } catch (e: OptimisticLockingFailureException) {
            StartOutcome.Skipped("다른 실행이 같은 회차를 먼저 이어받았음")
        }

    private fun runGuarded(
        run: PaymentReconciliationRun,
        source: PaymentReconciliationSourcePort,
    ): ReconcilePaymentsResult {
        val attempt = run.attempt
        var fetched = 0
        try {
            val seen = HashSet<String>()

            // 1. PG가 창 안에서 상태를 바꿨다고 말하는 건 전부. 목록은 낡을 수 있어 불일치는 단건으로 한 번 더 확인한다
            val records = source.fetchChangedPayments(run.windowFrom, run.windowUntil)
            fetched = records.size
            records.forEach { record ->
                seen += record.paymentKey
                val first = compare(run, attempt, source, record.paymentKey, record)
                if (first.mismatchType !=
                    MismatchType.MATCHED
                ) {
                    compare(run, attempt, source, record.paymentKey, source.findByPaymentKey(record.paymentKey))
                }
            }

            // 2. 우리 쪽에서 창 안에 바뀌었는데 PG 목록에는 없던 건 — 원장 전체를 설명하는 소스에서만, 단건으로 다시 묻는다
            if (source.coversInternalLedger) {
                val oursOnly = tx.execute { service.findOurPaymentKeysChangedIn(run.windowFrom, run.windowUntil) }!!.filter { seen.add(it) }
                oursOnly.forEach { key -> compare(run, attempt, source, key, source.findByPaymentKey(key)) }
            }

            // 3. 이전 회차에 열려 있던 건 — 창과 무관하게 다시 판정하고, 새 판정과 같은 트랜잭션에서 옛 건을 닫는다
            val carried = tx.execute { service.findOpenItemsForCarryOver(run, LocalDateTime.now()) }!!.groupBy { it.paymentKey }
            carried.forEach { (key, oldItems) ->
                val alreadyJudgedThisRun = !seen.add(key)
                val record = if (alreadyJudgedThisRun) null else source.findByPaymentKey(key)
                tx.execute {
                    if (!alreadyJudgedThisRun) service.recordComparison(run.id, attempt, key, record, source.supportsAutoConfirmation)
                    service.supersede(run.id, attempt, oldItems, LocalDateTime.now())
                }
            }

            // 4. 자동 보정 — 확정 본체로. 한 건의 실패가 회차를 멈추지 않는다
            tx.execute { service.findAutoPending(run.id) }!!.forEach { item -> resolveAuto(run, attempt, source, item) }

            val (completed, attention) = tx.execute { service.complete(run.id, attempt, fetched, LocalDateTime.now()) }!!
            alert.notifyRunFinished(completed, attention)
            return ReconcilePaymentsResult.from(completed, ReconcileOutcome.COMPLETED)
        } catch (e: BusinessException) {
            if (e.errorCode == PaymentErrorCode.RECONCILIATION_RUN_TAKEN_OVER) {
                log.warn("결제 대사 중단 — 다른 실행이 회차를 이어받음. runId={}, attempt={}", run.id, attempt)
                return ReconcilePaymentsResult.skipped(run.source, run.baseDate, "다른 실행이 회차를 이어받아 중단")
            }
            return recordFailure(run, attempt, fetched, e)
        } catch (e: Exception) {
            return recordFailure(run, attempt, fetched, e)
        }
    }

    private fun compare(
        run: PaymentReconciliationRun,
        attempt: Int,
        source: PaymentReconciliationSourcePort,
        key: String,
        record: GatewayPaymentRecord?,
    ): PaymentReconciliationItem = tx.execute { service.recordComparison(run.id, attempt, key, record, source.supportsAutoConfirmation) }!!

    /**
     * 확정 본체를 태우고 결과를 보고 적는다. "예외 없이 끝났다"가 아니라 결과 status가 PAID이고,
     * 그 뒤 최신 원장·PG로 다시 판정해 일치일 때만 보정 성공이다. 그 외의 재판정 결론은 그대로 남긴다.
     */
    private fun resolveAuto(
        run: PaymentReconciliationRun,
        attempt: Int,
        source: PaymentReconciliationSourcePort,
        item: PaymentReconciliationItem,
    ) {
        val now = LocalDateTime.now()
        try {
            tx.execute { service.assertOwnership(run.id, attempt) } // 소유권을 잃은 실행이 PG를 건드리지 않게 호출 직전에 확인
            val result = paymentUseCase.completePaymentByGateway(CompletePaymentByGatewayCommand(item.paymentKey))
            val rejudged = compare(run, attempt, source, item.paymentKey, source.findByPaymentKey(item.paymentKey))
            when {
                result?.status == CompletePaymentStatus.PAID && rejudged.mismatchType == MismatchType.MATCHED -> {
                    tx.execute { service.markAutoConfirmed(run.id, attempt, rejudged, item.mismatchType, now) }
                }

                rejudged.resolution == ReconciliationResolution.AUTO_PENDING -> {
                    // 목록에서는 승인이었는데 본체 재조회는 아니라고 하거나, 확정 뒤에도 보정 대상으로 남는다 — 실패로 남겨 이월한다
                    tx.execute {
                        service.markFailed(
                            run.id,
                            attempt,
                            rejudged,
                            "확정 본체 결과 ${result?.status ?: "결제 없음"} — 재판정에서도 보정 대상",
                            now,
                        )
                    }
                }

                else -> {
                    // 재판정이 일치·사람 확인 등 다른 결론을 냈으면 그 결론이 기록이다
                }
            }
        } catch (e: BusinessException) {
            when (e.errorCode) {
                PaymentErrorCode.RECONCILIATION_RUN_TAKEN_OVER -> {
                    throw e
                }

                // 돈이 걸린 판단은 사람 몫 — 본체가 거절한 이유를 그대로 유형으로 적는다
                PaymentErrorCode.PAYMENT_AMOUNT_MISMATCH -> {
                    tx.execute {
                        service.markManualRequired(run.id, attempt, item, MismatchType.AMOUNT_MISMATCH, e.message ?: "금액 불일치", now)
                    }
                }

                PaymentErrorCode.PAYMENT_ORDER_GROUP_CANCELLED -> {
                    tx.execute {
                        service.markManualRequired(
                            run.id,
                            attempt,
                            item,
                            MismatchType.PG_PAID_GROUP_CANCELLED,
                            e.message ?: "취소된 주문그룹",
                            now,
                        )
                    }
                }

                else -> {
                    log.warn("대사 자동 보정 실패 — 다음 회차가 이월해 재시도. paymentKey={}, errorCode={}", item.paymentKey, e.errorCode.code, e)
                    tx.execute { service.markFailed(run.id, attempt, item, "${e.errorCode.code}: ${e.message}", now) }
                }
            }
        } catch (e: Exception) {
            log.warn("대사 자동 보정 실패 — 다음 회차가 이월해 재시도. paymentKey={}", item.paymentKey, e)
            tx.execute { service.markFailed(run.id, attempt, item, e.toString(), now) }
        }
    }

    private fun recordFailure(
        run: PaymentReconciliationRun,
        attempt: Int,
        fetched: Int,
        e: Exception,
    ): ReconcilePaymentsResult {
        log.error("결제 대사 회차 실패. source={}, baseDate={}, attempt={}", run.source, run.baseDate, attempt, e)
        val failed = tx.execute { service.fail(run.id, attempt, fetched, e.toString(), LocalDateTime.now()) }
        return if (failed == null) {
            ReconcilePaymentsResult.skipped(run.source, run.baseDate, "실패했으나 회차 소유권을 잃어 기록하지 않음")
        } else {
            ReconcilePaymentsResult.from(failed, ReconcileOutcome.FAILED, e.toString())
        }
    }
}
