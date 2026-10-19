package com.aechak.application.payment.reconciliation.facade

import com.aechak.application.payment.port.GatewayPaymentRecord
import com.aechak.application.payment.port.PaymentGatewayStatus
import com.aechak.application.payment.port.PaymentReconciliationAlertPort
import com.aechak.application.payment.port.PaymentReconciliationSourcePort
import com.aechak.application.payment.reconciliation.service.PaymentReconciliationService
import com.aechak.application.payment.reconciliation.usecase.command.CatchUpReconciliationCommand
import com.aechak.application.payment.reconciliation.usecase.command.ReconcilePaymentsCommand
import com.aechak.application.payment.reconciliation.usecase.result.ReconcileOutcome
import com.aechak.application.payment.service.PaymentService
import com.aechak.application.payment.usecase.PaymentUseCase
import com.aechak.application.payment.usecase.command.CancelPaymentCommand
import com.aechak.application.payment.usecase.command.CompletePaymentByGatewayCommand
import com.aechak.application.payment.usecase.command.CompletePaymentByWebhookCommand
import com.aechak.application.payment.usecase.command.CompletePaymentCommand
import com.aechak.application.payment.usecase.command.PreparePaymentCommand
import com.aechak.application.payment.usecase.result.CancelPaymentResult
import com.aechak.application.payment.usecase.result.CompletePaymentResult
import com.aechak.application.payment.usecase.result.CompletePaymentStatus
import com.aechak.application.payment.usecase.result.PreparePaymentResult
import com.aechak.common.error.BusinessException
import com.aechak.domain.order.group.DeliveryAddressSnapshot
import com.aechak.domain.order.group.OrderGroup
import com.aechak.domain.order.group.repository.OrderGroupRepository
import com.aechak.domain.payment.Payment
import com.aechak.domain.payment.enums.PaymentMethod
import com.aechak.domain.payment.error.PaymentErrorCode
import com.aechak.domain.payment.reconciliation.MismatchType
import com.aechak.domain.payment.reconciliation.PaymentReconciliationItem
import com.aechak.domain.payment.reconciliation.PaymentReconciliationRun
import com.aechak.domain.payment.reconciliation.ReconciliationResolution
import com.aechak.domain.payment.reconciliation.ReconciliationRunStatus
import com.aechak.domain.payment.reconciliation.ReconciliationSource
import com.aechak.domain.payment.reconciliation.repository.PaymentReconciliationItemRepository
import com.aechak.domain.payment.reconciliation.repository.PaymentReconciliationRunRepository
import com.aechak.domain.payment.repository.PaymentRepository
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.dao.OptimisticLockingFailureException
import org.springframework.transaction.TransactionDefinition
import org.springframework.transaction.support.AbstractPlatformTransactionManager
import org.springframework.transaction.support.DefaultTransactionStatus
import java.time.LocalDate
import java.time.LocalDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * 회차 오케스트레이션 계약. 실 DB 없이 페이크로 "같은 날을 두 번 돌리면 어떻게 되나"를 고정한다.
 * 깨지면 같은 날이 두 번 처리되거나, 죽은 회차를 아무도 이어받지 못하거나, 한 건의 실패가 회차 전체를 죽인다.
 */
class PaymentReconciliationFacadeTest {
    private val runs = FakeRunRepository()
    private val items = FakeItemRepository().also { it.runs = runs }
    private val payments = FakePaymentRepository()
    private val groups = FakeOrderGroupRepository()
    private val source = FakeSource()
    private val confirmations = FakePaymentUseCase { key -> settleInFakes(key) }
    private val alerts = RecordingAlert()
    private val facade =
        PaymentReconciliationFacade(
            PaymentReconciliationService(runs, items, PaymentService(payments, groups)),
            listOf(source),
            confirmations,
            alerts,
            NoOpTransactionManager(),
        )
    private val baseDate = LocalDate.of(2026, 10, 15)

    private fun command() = ReconcilePaymentsCommand(ReconciliationSource.PORTONE, baseDate)

    private fun seedPendingPayment(key: String): Payment {
        val group = groups.add(group())
        return payments.add(Payment.prepare(group.id, key, PaymentMethod.CARD, group.finalPaymentAmount))
    }

    /** 확정 본체가 했을 일을 페이크 원장에 반영한다 — 결제 승인 기록 + 그룹 결제완료 */
    private fun settleInFakes(key: String) {
        val payment = payments.findByPaymentId(key) ?: return
        payments.add(payment.approve("pg-tx", payment.targetAmount))
        groups.findById(payment.orderGroupId)?.markPaid()
    }

    private fun pgPaid(key: String) =
        GatewayPaymentRecord(key, PaymentGatewayStatus.PAID, 13_000L, 13_000L, 0L, "pg-tx", LocalDateTime.of(2026, 10, 15, 10, 0))

    @Test
    fun `창은 기준일 전날 자정부터 다음 날 자정까지다`() {
        facade.reconcile(command())

        assertEquals(listOf(LocalDateTime.of(2026, 10, 14, 0, 0) to LocalDateTime.of(2026, 10, 16, 0, 0)), source.fetchWindows)
    }

    @Test
    fun `완료된 회차를 다시 돌리면 아무것도 하지 않고 SKIPPED다`() {
        val first = facade.reconcile(command())
        val second = facade.reconcile(command())

        assertEquals(ReconcileOutcome.COMPLETED, first.outcome)
        assertEquals(ReconcileOutcome.SKIPPED, second.outcome)
        assertEquals(1, source.fetchWindows.size, "두 번째 호출은 PG를 부르지 않아야 한다")
        assertEquals(1, runs.all().size)
    }

    @Test
    fun `실패한 회차는 다음 호출이 이어받고 시도 횟수가 오른다`() {
        source.failNextList()

        val failed = facade.reconcile(command())
        val retried = facade.reconcile(command())

        assertEquals(ReconcileOutcome.FAILED, failed.outcome)
        assertEquals(ReconcileOutcome.COMPLETED, retried.outcome)
        assertEquals(2, retried.attempt)
        assertEquals(ReconciliationRunStatus.COMPLETED, runs.all().single().status)
    }

    @Test
    fun `기한 안의 RUNNING 회차는 다른 실행이 손대지 않는다`() {
        val window = PaymentReconciliationService(runs, items, PaymentService(payments, groups)).windowOf(baseDate)
        runs.save(
            PaymentReconciliationRun.start(
                ReconciliationSource.PORTONE,
                baseDate,
                window.from,
                window.until,
                LocalDateTime.now().minusMinutes(5),
            ),
        )

        val result = facade.reconcile(command())

        assertEquals(ReconcileOutcome.SKIPPED, result.outcome)
        assertTrue(source.fetchWindows.isEmpty())
    }

    @Test
    fun `기한을 넘긴 RUNNING 회차는 죽은 것으로 보고 이어받는다`() {
        val window = PaymentReconciliationService(runs, items, PaymentService(payments, groups)).windowOf(baseDate)
        runs.save(
            PaymentReconciliationRun.start(
                ReconciliationSource.PORTONE,
                baseDate,
                window.from,
                window.until,
                LocalDateTime.now().minusHours(3),
            ),
        )

        val result = facade.reconcile(command())

        assertEquals(ReconcileOutcome.COMPLETED, result.outcome)
        assertEquals(2, result.attempt)
    }

    @Test
    fun `회차를 여는 경쟁에서 UNIQUE에 지면 SKIPPED로 물러난다`() {
        runs.failNextInsert()

        val result = facade.reconcile(command())

        assertEquals(ReconcileOutcome.SKIPPED, result.outcome)
    }

    @Test
    fun `PG 승인이고 우리가 결제대기면 확정 본체를 태워 자동 보정한다`() {
        val payment = seedPendingPayment("pay-1")
        source.stub(pgPaid("pay-1"))
        confirmations.respondWith(CompletePaymentStatus.PAID)

        val result = facade.reconcile(command())

        assertEquals(listOf("pay-1"), confirmations.calls)
        val item = items.byKey("pay-1")
        assertEquals(MismatchType.PG_PAID_OURS_UNCONFIRMED, item.mismatchType)
        assertEquals(ReconciliationResolution.AUTO_CONFIRMED, item.resolution)
        assertEquals(1, result.counts.autoResolved)
        assertEquals(payment.id, item.ours?.paymentId)
    }

    @Test
    fun `확정 본체가 승인이 아니라고 답하면 자동 보정으로 적지 않고 다시 판정한다`() {
        seedPendingPayment("pay-2")
        source.stub(pgPaid("pay-2"))
        confirmations.respondWith(CompletePaymentStatus.NOT_STARTED)

        val result = facade.reconcile(command())

        val item = items.byKey("pay-2")
        assertEquals(ReconciliationResolution.FAILED, item.resolution, "목록은 승인인데 재조회가 아니면 거짓 해결 대신 실패로 남겨 이월한다")
        assertEquals(1, result.counts.failed)
        assertTrue(source.findCalledWith.contains("pay-2"), "재판정은 PG 단건을 다시 묻는다")
    }

    @Test
    fun `확정 본체가 금액 불일치로 거절하면 사람 확인으로 바꾼다`() {
        seedPendingPayment("pay-3")
        source.stub(pgPaid("pay-3"))
        confirmations.failWith(PaymentErrorCode.PAYMENT_AMOUNT_MISMATCH)

        facade.reconcile(command())

        val item = items.byKey("pay-3")
        assertEquals(MismatchType.AMOUNT_MISMATCH, item.mismatchType)
        assertEquals(ReconciliationResolution.MANUAL_REQUIRED, item.resolution)
    }

    @Test
    fun `한 건의 게이트웨이 장애가 회차를 죽이지 않는다`() {
        seedPendingPayment("pay-4")
        seedPendingPayment("pay-5")
        source.stub(pgPaid("pay-4"))
        source.stub(pgPaid("pay-5"))
        confirmations.failOnceWith(PaymentErrorCode.PAYMENT_GATEWAY_ERROR)
        confirmations.respondWith(CompletePaymentStatus.PAID)

        val result = facade.reconcile(command())

        assertEquals(ReconcileOutcome.COMPLETED, result.outcome)
        assertEquals(1, result.counts.failed)
        assertEquals(1, result.counts.autoResolved)
        assertTrue(result.counts.needsAttention)
        assertEquals(1, alerts.attentionCounts.single())
    }

    @Test
    fun `우리 쪽에서 바뀌었는데 PG 목록에 없는 건은 단건으로 다시 묻는다`() {
        val group = groups.add(group().also { it.markPaid() })
        val approved = Payment.prepare(group.id, "pay-6", PaymentMethod.CARD, group.finalPaymentAmount).approve("pg-tx", 13_000L)
        payments.add(approved)
        payments.changedKeys = listOf("pay-6")

        val result = facade.reconcile(command())

        assertEquals(listOf("pay-6"), source.findCalledWith)
        val item = items.byKey("pay-6")
        assertEquals(MismatchType.PG_NOT_PAID_OURS_APPROVED, item.mismatchType)
        assertEquals(1, result.counts.manualRequired)
    }

    @Test
    fun `이전 회차에 열려 있던 건은 창과 무관하게 다시 판정하고 옛 건은 닫는다`() {
        seedPendingPayment("pay-7")
        val earlierDate = baseDate.minusDays(1)
        source.stub(pgPaid("pay-7").copy(statusChangedAt = earlierDate.atTime(10, 0))) // 첫 회차 창 안
        confirmations.respondWith(CompletePaymentStatus.NOT_STARTED)
        facade.reconcile(ReconcilePaymentsCommand(ReconciliationSource.PORTONE, earlierDate))
        val old = items.byKey("pay-7")
        assertEquals(ReconciliationResolution.FAILED, old.resolution)

        confirmations.respondWith(CompletePaymentStatus.PAID)
        val laterDate = baseDate.plusDays(5) // 창 밖 — 목록에는 안 잡히고 이월로만 온다
        val result = facade.reconcile(ReconcilePaymentsCommand(ReconciliationSource.PORTONE, laterDate))

        val fresh = items.all().filter { it.paymentKey == "pay-7" }
        assertEquals(2, fresh.size, "옛 회차의 건과 이번 회차의 건이 따로 남는다")
        assertEquals(ReconciliationResolution.SUPERSEDED, fresh.first { it.runId == old.runId }.resolution)
        assertEquals(ReconciliationResolution.AUTO_CONFIRMED, fresh.first { it.runId == result.runId }.resolution)
        assertEquals(listOf("pay-7"), source.findCalledWith.takeLast(1), "이월 건은 단건으로 다시 묻는다")
    }

    @Test
    fun `따라잡기는 마지막 완료 다음 날부터 순서대로 돌리고 처음이면 어제만 돈다`() {
        val firstEver = facade.catchUp(CatchUpReconciliationCommand(ReconciliationSource.PORTONE, baseDate))
        assertEquals(listOf(baseDate), firstEver.map { it.baseDate })

        val later = facade.catchUp(CatchUpReconciliationCommand(ReconciliationSource.PORTONE, baseDate.plusDays(3)))

        assertEquals(listOf(baseDate.plusDays(1), baseDate.plusDays(2), baseDate.plusDays(3)), later.map { it.baseDate })
        assertTrue(later.all { it.outcome == ReconcileOutcome.COMPLETED })
    }

    @Test
    fun `따라잡기는 상한을 넘는 과거는 메우지 않고 한 날이 실패하면 멈춘다`() {
        facade.reconcile(command())
        source.failNextList()

        val results = facade.catchUp(CatchUpReconciliationCommand(ReconciliationSource.PORTONE, baseDate.plusDays(20), maxDays = 3))

        assertEquals(listOf(baseDate.plusDays(18)), results.map { it.baseDate }, "상한 3일이면 18·19·20일만 대상이고 첫날 실패에서 멈춘다")
        assertEquals(ReconcileOutcome.FAILED, results.single().outcome)
    }

    @Test
    fun `목록 판정이 불일치면 단건으로 한 번 더 확인해 최신 결론을 남긴다`() {
        val group = groups.add(group().also { it.markPaid() })
        payments.add(Payment.prepare(group.id, "pay-8", PaymentMethod.CARD, group.finalPaymentAmount).approve("pg-tx", 13_000L))
        // 목록은 낡아서 FAILED라고 하지만 단건은 승인
        source.stubListOnly(pgPaid("pay-8").copy(status = PaymentGatewayStatus.FAILED, paidAmount = 0L))
        source.stubSingle(pgPaid("pay-8"))

        val result = facade.reconcile(command())

        assertEquals(listOf("pay-8"), source.findCalledWith)
        assertEquals(MismatchType.MATCHED, items.byKey("pay-8").mismatchType, "낡은 목록으로 '우리만 승인'을 남기면 안 된다")
        assertEquals(0, result.counts.manualRequired)
    }

    @Test
    fun `자동 보정을 지원하지 않는 소스의 승인은 사람 확인으로 남긴다`() {
        val toss = FakeSource(ReconciliationSource.TOSS, coversLedger = false, autoConfirm = false)
        val facadeWithToss =
            PaymentReconciliationFacade(
                PaymentReconciliationService(runs, items, PaymentService(payments, groups)),
                listOf(toss),
                confirmations,
                alerts,
                NoOpTransactionManager(),
            )
        seedPendingPayment("pay-9")
        toss.stub(pgPaid("pay-9"))
        payments.changedKeys = listOf("pay-9", "pay-other")

        facadeWithToss.reconcile(ReconcilePaymentsCommand(ReconciliationSource.TOSS, baseDate))

        val item = items.byKey("pay-9")
        assertEquals(MismatchType.PG_PAID_OURS_UNCONFIRMED, item.mismatchType)
        assertEquals(ReconciliationResolution.MANUAL_REQUIRED, item.resolution, "확정 본체는 포트원에만 물으므로 토스 승인은 자동 보정하지 않는다")
        assertTrue(confirmations.calls.isEmpty())
        assertTrue(toss.findCalledWith.none { it == "pay-other" }, "원장 전체를 설명하지 않는 소스는 우리만 있는 건을 묻지 않는다")
    }

    @Test
    fun `회차가 보정 전에 죽으면 보정 대기 건이 다음 회차로 이월된다`() {
        seedPendingPayment("pay-10")
        val earlier = baseDate.minusDays(1)
        val window = PaymentReconciliationService(runs, items, PaymentService(payments, groups)).windowOf(earlier)
        val dead =
            runs.save(
                PaymentReconciliationRun.start(
                    ReconciliationSource.PORTONE,
                    earlier,
                    window.from,
                    window.until,
                    LocalDateTime.now().minusHours(3),
                ),
            )
        items.save(
            PaymentReconciliationItem.compare(
                dead.id,
                ReconciliationSource.PORTONE,
                "pay-10",
                null,
                null,
                MismatchType.PG_PAID_OURS_UNCONFIRMED,
                ReconciliationResolution.AUTO_PENDING,
            ),
        )
        source.stub(pgPaid("pay-10").copy(statusChangedAt = LocalDateTime.of(2026, 10, 1, 9, 0))) // 어느 창에도 안 든다
        confirmations.respondWith(CompletePaymentStatus.PAID)

        val result = facade.reconcile(command())

        val fresh = items.all().filter { it.paymentKey == "pay-10" }
        assertEquals(ReconciliationResolution.SUPERSEDED, fresh.first { it.runId == dead.id }.resolution)
        assertEquals(ReconciliationResolution.AUTO_CONFIRMED, fresh.first { it.runId == result.runId }.resolution)
    }

    @Test
    fun `살아서 도는 다른 날짜 회차의 열린 건은 닫지 않는다`() {
        val later = baseDate.plusDays(1)
        val window = PaymentReconciliationService(runs, items, PaymentService(payments, groups)).windowOf(later)
        val alive =
            runs.save(
                PaymentReconciliationRun.start(ReconciliationSource.PORTONE, later, window.from, window.until, LocalDateTime.now()),
            )
        items.save(
            PaymentReconciliationItem.compare(
                alive.id,
                ReconciliationSource.PORTONE,
                "pay-11",
                null,
                null,
                MismatchType.PG_ONLY_UNKNOWN,
                ReconciliationResolution.MANUAL_REQUIRED,
            ),
        )

        facade.reconcile(command())

        assertEquals(ReconciliationResolution.MANUAL_REQUIRED, items.all().single { it.paymentKey == "pay-11" }.resolution)
        assertTrue(source.findCalledWith.none { it == "pay-11" })
    }

    @Test
    fun `따라잡기는 기간 안의 실패한 날도 다시 돈다`() {
        facade.reconcile(ReconcilePaymentsCommand(ReconciliationSource.PORTONE, baseDate.minusDays(2)))
        source.failNextList()
        facade.reconcile(ReconcilePaymentsCommand(ReconciliationSource.PORTONE, baseDate.minusDays(1))) // FAILED
        facade.reconcile(command()) // COMPLETED — 마지막 완료일은 baseDate

        val results = facade.catchUp(CatchUpReconciliationCommand(ReconciliationSource.PORTONE, baseDate.plusDays(1)))

        assertEquals(listOf(baseDate.minusDays(1), baseDate.plusDays(1)), results.map { it.baseDate }, "마지막 완료일 이전의 실패한 날도 다시 돈다")
        assertTrue(results.all { it.outcome == ReconcileOutcome.COMPLETED })
    }

    @Test
    fun `확정 본체가 승인이라 해도 재판정이 일치가 아니면 자동 보정으로 적지 않는다`() {
        seedPendingPayment("pay-12")
        source.stub(pgPaid("pay-12"))
        confirmations.respondWith(CompletePaymentStatus.PAID)
        // 확정 뒤 단건 재조회가 다른 승인액을 돌려준다 — 원장과 PG가 어긋난 채 남는다
        source.stubSingle(pgPaid("pay-12").copy(paidAmount = 12_000L))

        val result = facade.reconcile(command())

        val item = items.byKey("pay-12")
        assertEquals(MismatchType.AMOUNT_MISMATCH, item.mismatchType)
        assertEquals(ReconciliationResolution.MANUAL_REQUIRED, item.resolution, "PAID 결과만 믿고 AUTO_CONFIRMED로 닫으면 거짓 해결이다")
        assertEquals(0, result.counts.autoResolved)
    }

    @Test
    fun `이월 후보를 뽑은 뒤 옛 회차가 되살아났으면 그 회차의 건은 닫지 않는다`() {
        val earlier = baseDate.minusDays(1)
        val window = PaymentReconciliationService(runs, items, PaymentService(payments, groups)).windowOf(earlier)
        val old =
            runs.save(
                PaymentReconciliationRun.start(
                    ReconciliationSource.PORTONE,
                    earlier,
                    window.from,
                    window.until,
                    LocalDateTime.now().minusHours(3),
                ),
            )
        items.save(
            PaymentReconciliationItem.compare(
                old.id,
                ReconciliationSource.PORTONE,
                "pay-13",
                null,
                null,
                MismatchType.PG_ONLY_UNKNOWN,
                ReconciliationResolution.MANUAL_REQUIRED,
            ),
        )
        // 후보 조회 직후 옛 회차가 이어받아 살아난 상황을 흉내 낸다 — 단건 조회 시점에 되살린다
        source.onFind = { runs.save(runs.findById(old.id)!!.resume(LocalDateTime.now())) }

        facade.reconcile(command())

        assertEquals(
            ReconciliationResolution.MANUAL_REQUIRED,
            items.all().single { it.runId == old.id && it.paymentKey == "pay-13" }.resolution,
        )
    }

    // ---------- 페이크 ----------

    private fun group(): OrderGroup =
        OrderGroup.create(
            buyerId = 7L,
            deliveryAddressId = 1L,
            deliveryAddress = DeliveryAddressSnapshot("n", "c", "12345", "서울", null, null),
            usedPoint = 0L,
            totalProductAmount = 13_000L,
            totalShippingFee = 0L,
            idempotencyKey = "key-${System.nanoTime()}",
            expiresAt = LocalDateTime.now().plusMinutes(10),
        )

    private class FakeRunRepository : PaymentReconciliationRunRepository {
        private val rows = mutableMapOf<Long, PaymentReconciliationRun>()
        private var nextId = 1L
        private var failInsert = false

        fun all() = rows.values.toList()

        fun failNextInsert() {
            failInsert = true
        }

        override fun save(run: PaymentReconciliationRun): PaymentReconciliationRun {
            if (run.id == 0L) {
                if (failInsert) {
                    failInsert = false
                    throw DataIntegrityViolationException("uk_payment_reconciliation_runs_source_base_date")
                }
                if (rows.values.any { it.source == run.source && it.baseDate == run.baseDate }) {
                    throw DataIntegrityViolationException("uk_payment_reconciliation_runs_source_base_date")
                }
                val saved = restoreWith(run, nextId++, run.version)
                rows[saved.id] = saved
                return saved
            }
            val current = rows[run.id] ?: throw IllegalStateException("없는 회차 ${run.id}")
            if (current.version != run.version) throw OptimisticLockingFailureException("version mismatch")
            val saved = restoreWith(run, run.id, run.version + 1)
            rows[saved.id] = saved
            return saved
        }

        override fun findBySourceAndBaseDate(
            source: ReconciliationSource,
            baseDate: LocalDate,
        ): PaymentReconciliationRun? = rows.values.find { it.source == source && it.baseDate == baseDate }

        override fun findById(id: Long): PaymentReconciliationRun? = rows[id]

        override fun findByIdForUpdate(id: Long): PaymentReconciliationRun? = rows[id]

        override fun findLatestCompletedBaseDate(source: ReconciliationSource): LocalDate? =
            rows.values.filter { it.source == source && it.status == ReconciliationRunStatus.COMPLETED }.maxOfOrNull { it.baseDate }

        override fun findIncompleteBaseDates(
            source: ReconciliationSource,
            from: LocalDate,
            until: LocalDate,
        ): List<LocalDate> =
            rows.values
                .filter { it.source == source && it.status != ReconciliationRunStatus.COMPLETED && it.baseDate in from..until }
                .map { it.baseDate }
                .sorted()

        private fun restoreWith(
            run: PaymentReconciliationRun,
            id: Long,
            version: Int,
        ) = PaymentReconciliationRun.restore(
            id,
            run.source,
            run.baseDate,
            run.windowFrom,
            run.windowUntil,
            run.status,
            run.attempt,
            run.counts,
            run.startedAt,
            run.finishedAt,
            run.errorMessage,
            version,
        )
    }

    private class FakeItemRepository : PaymentReconciliationItemRepository {
        private val rows = mutableMapOf<Long, PaymentReconciliationItem>()
        private var nextId = 1L

        fun all() = rows.values.toList()

        fun byKey(key: String) = rows.values.single { it.paymentKey == key && it.resolution != ReconciliationResolution.SUPERSEDED }

        override fun save(item: PaymentReconciliationItem): PaymentReconciliationItem {
            val id = if (item.id == 0L) nextId++ else item.id
            val saved =
                PaymentReconciliationItem.restore(
                    id,
                    item.runId,
                    item.source,
                    item.paymentKey,
                    item.ours,
                    item.gateway,
                    item.mismatchType,
                    item.resolution,
                    item.resolutionNote,
                    item.resolvedAt,
                )
            rows[id] = saved
            return saved
        }

        override fun findByRunIdAndPaymentKey(
            runId: Long,
            paymentKey: String,
        ) = rows.values.find { it.runId == runId && it.paymentKey == paymentKey }

        override fun findAllByRunIdAndResolution(
            runId: Long,
            resolution: ReconciliationResolution,
        ) = rows.values.filter { it.runId == runId && it.resolution == resolution }

        override fun findAllByRunId(runId: Long) = rows.values.filter { it.runId == runId }

        lateinit var runs: FakeRunRepository

        override fun findOpenItemsForCarryOver(
            source: ReconciliationSource,
            beforeBaseDate: LocalDate,
            staleBefore: LocalDateTime,
        ) = rows.values.filter { item ->
            val run = runs.findById(item.runId)!!
            item.source == source &&
                item.resolution.isOpen &&
                run.baseDate.isBefore(beforeBaseDate) &&
                (run.status != ReconciliationRunStatus.RUNNING || run.startedAt.isBefore(staleBefore))
        }

        override fun findById(id: Long) = rows[id]
    }

    private class FakePaymentRepository : PaymentRepository {
        private val rows = mutableMapOf<String, Payment>()
        private var nextId = 1L
        var changedKeys: List<String> = emptyList()

        fun add(payment: Payment): Payment = save(payment)

        override fun save(payment: Payment): Payment {
            val id = if (payment.id == 0L) nextId++ else payment.id
            val saved =
                Payment.restore(
                    id,
                    payment.orderGroupId,
                    payment.paymentId,
                    payment.method,
                    payment.targetAmount,
                    payment.status,
                    payment.transactionId,
                    payment.realPaidAmount,
                    payment.failureCode,
                    payment.cancellableAmount,
                    payment.cancelledAmount,
                    payment.cancelRequestedAt,
                    payment.version,
                )
            rows[saved.paymentId] = saved
            return saved
        }

        override fun findByOrderGroupId(orderGroupId: Long) = rows.values.find { it.orderGroupId == orderGroupId }

        override fun findByPaymentId(paymentId: String) = rows[paymentId]

        override fun findPaymentIdsUpdatedBetween(
            from: LocalDateTime,
            until: LocalDateTime,
        ) = changedKeys
    }

    private class FakeOrderGroupRepository : OrderGroupRepository {
        private val rows = mutableMapOf<Long, OrderGroup>()
        private var nextId = 1L

        fun add(group: OrderGroup): OrderGroup {
            val id = nextId++
            OrderGroup::class.java
                .getDeclaredField("id")
                .apply { isAccessible = true }
                .set(group, id)
            rows[id] = group
            return group
        }

        override fun save(orderGroup: OrderGroup) = add(orderGroup)

        override fun findById(id: Long) = rows[id]

        override fun findByIdempotencyKey(idempotencyKey: String) = rows.values.find { it.idempotencyKey == idempotencyKey }

        override fun findByPublicId(publicId: String) = rows.values.find { it.publicId == publicId }

        override fun markPaidIfPending(id: Long): Boolean = throw UnsupportedOperationException()
    }

    private class FakeSource(
        override val source: ReconciliationSource = ReconciliationSource.PORTONE,
        coversLedger: Boolean = true,
        autoConfirm: Boolean = true,
    ) : PaymentReconciliationSourcePort {
        private val records = mutableMapOf<String, GatewayPaymentRecord>()
        private val singles = mutableMapOf<String, GatewayPaymentRecord>()
        private var failList = false
        val fetchWindows = mutableListOf<Pair<LocalDateTime, LocalDateTime>>()
        val findCalledWith = mutableListOf<String>()
        var onFind: (() -> Unit)? = null

        override val coversInternalLedger = coversLedger
        override val supportsAutoConfirmation = autoConfirm

        fun stub(record: GatewayPaymentRecord) {
            records[record.paymentKey] = record
            singles[record.paymentKey] = record
        }

        fun stubListOnly(record: GatewayPaymentRecord) {
            records[record.paymentKey] = record
        }

        fun stubSingle(record: GatewayPaymentRecord) {
            singles[record.paymentKey] = record
        }

        fun failNextList() {
            failList = true
        }

        override fun fetchChangedPayments(
            from: LocalDateTime,
            until: LocalDateTime,
        ): List<GatewayPaymentRecord> {
            fetchWindows += from to until
            if (failList) {
                failList = false
                throw BusinessException(PaymentErrorCode.RECONCILIATION_SOURCE_ERROR)
            }
            return records.values.filter { r -> r.statusChangedAt?.let { !it.isBefore(from) && it.isBefore(until) } ?: false }
        }

        override fun findByPaymentKey(paymentKey: String): GatewayPaymentRecord? {
            findCalledWith += paymentKey
            onFind?.invoke()
            return singles[paymentKey]
        }
    }

    private class FakePaymentUseCase(
        private val onPaid: (String) -> Unit,
    ) : PaymentUseCase {
        val calls = mutableListOf<String>()
        private var status: CompletePaymentStatus = CompletePaymentStatus.PAID
        private var failAlways: PaymentErrorCode? = null
        private var failOnce: PaymentErrorCode? = null

        fun respondWith(status: CompletePaymentStatus) {
            this.status = status
            failAlways = null
        }

        fun failWith(code: PaymentErrorCode) {
            failAlways = code
        }

        fun failOnceWith(code: PaymentErrorCode) {
            failOnce = code
        }

        override fun completePaymentByGateway(command: CompletePaymentByGatewayCommand): CompletePaymentResult? {
            calls += command.paymentId
            failOnce?.let {
                failOnce = null
                throw BusinessException(it)
            }
            failAlways?.let { throw BusinessException(it) }
            if (status == CompletePaymentStatus.PAID) onPaid(command.paymentId)
            return CompletePaymentResult(status, command.paymentId, 13_000L, 0L, null)
        }

        override fun preparePayment(command: PreparePaymentCommand): PreparePaymentResult = throw UnsupportedOperationException()

        override fun completePayment(command: CompletePaymentCommand): CompletePaymentResult = throw UnsupportedOperationException()

        override fun completePaymentByWebhook(command: CompletePaymentByWebhookCommand): CompletePaymentResult? =
            throw UnsupportedOperationException()

        /** 대사는 취소를 집행하지 않는다 — 호출되면 테스트 전제가 깨진 것 */
        override fun cancelPayment(command: CancelPaymentCommand): CancelPaymentResult = throw UnsupportedOperationException()
    }

    private class RecordingAlert : PaymentReconciliationAlertPort {
        val attentionCounts = mutableListOf<Int>()

        override fun notifyRunFinished(
            run: PaymentReconciliationRun,
            attentionItems: List<PaymentReconciliationItem>,
        ) {
            attentionCounts += attentionItems.size
        }
    }

    private class NoOpTransactionManager : AbstractPlatformTransactionManager() {
        override fun doGetTransaction(): Any = Any()

        override fun doBegin(
            transaction: Any,
            definition: TransactionDefinition,
        ) = Unit

        override fun doCommit(status: DefaultTransactionStatus) = Unit

        override fun doRollback(status: DefaultTransactionStatus) = Unit
    }
}
