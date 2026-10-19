package com.aechak.api.payment

import com.aechak.api.support.FakePaymentGateway
import com.aechak.api.support.FakePaymentReconciliationSource
import com.aechak.api.support.IntegrationTestBase
import com.aechak.application.payment.port.GatewayPaymentRecord
import com.aechak.application.payment.port.PaymentGatewayStatus
import com.aechak.application.payment.port.PaymentGatewayView
import com.aechak.application.payment.reconciliation.usecase.PaymentReconciliationUseCase
import com.aechak.application.payment.reconciliation.usecase.command.ReconcilePaymentsCommand
import com.aechak.application.payment.reconciliation.usecase.result.ReconcileOutcome
import com.aechak.domain.order.group.DeliveryAddressSnapshot
import com.aechak.domain.order.group.OrderGroup
import com.aechak.domain.order.order.Order
import com.aechak.domain.order.order.OrderItem
import com.aechak.domain.payment.Payment
import com.aechak.domain.payment.enums.PaymentMethod
import com.aechak.domain.payment.reconciliation.MismatchType
import com.aechak.domain.payment.reconciliation.PaymentReconciliationRun
import com.aechak.domain.payment.reconciliation.ReconciliationResolution
import com.aechak.domain.payment.reconciliation.ReconciliationRunStatus
import com.aechak.domain.payment.reconciliation.ReconciliationSource
import com.aechak.domain.payment.reconciliation.repository.PaymentReconciliationItemRepository
import com.aechak.domain.payment.reconciliation.repository.PaymentReconciliationRunRepository
import com.aechak.domain.payment.repository.PaymentRepository
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.UUID

/**
 * 결제 대사 통합. 실 MySQL에서 회차 멱등(UNIQUE)·자동 보정이 실제 확정 본체를 타는지·사람 확인 분류·이월을 고정한다.
 * PG는 Fake 소스(목록·단건)와 Fake 게이트웨이(확정 본체의 재조회)로 대신한다 — 둘 다 같은 결제를 보도록 심는다.
 */
class PaymentReconciliationIntegrationTest : IntegrationTestBase() {
    @Autowired
    private lateinit var useCase: PaymentReconciliationUseCase

    @Autowired
    private lateinit var source: FakePaymentReconciliationSource

    @Autowired
    private lateinit var gateway: FakePaymentGateway

    @Autowired
    private lateinit var payments: PaymentRepository

    @Autowired
    private lateinit var runs: PaymentReconciliationRunRepository

    @Autowired
    private lateinit var items: PaymentReconciliationItemRepository

    private val baseDate = LocalDate.of(2026, 10, 15)
    private val inWindow = LocalDateTime.of(2026, 10, 15, 14, 0)

    @BeforeEach
    fun resetFakes() {
        source.clear()
        gateway.clear()
    }

    // ---------- 픽스처 ----------

    private fun seedGroup(
        buyerId: Long,
        amount: Long = 13_000L,
    ): OrderGroup =
        tx.execute {
            val group =
                OrderGroup.create(
                    buyerId = buyerId,
                    deliveryAddressId = 1L,
                    deliveryAddress = DeliveryAddressSnapshot("enc-name", "enc-contact", "12345", "서울시 애착구 멍냥로 1", null, null),
                    usedPoint = 0L,
                    totalProductAmount = amount,
                    totalShippingFee = 0L,
                    idempotencyKey = "key-${UUID.randomUUID()}",
                    expiresAt = LocalDateTime.now().plusMinutes(10),
                )
            em.persist(group)
            em.persist(
                Order.create(
                    orderGroup = group,
                    sellerId = 71L,
                    sellerNameSnapshot = "멍멍상회",
                    allocatedCouponDiscount = 0L,
                    sellerShippingFee = 0L,
                    baseShippingFeeSnapshot = null,
                    freeShippingThresholdSnapshot = null,
                    items = listOf(OrderItem.of(1L, 501L, "기본", 1, amount, 0L, 1L)),
                ),
            )
            em.flush()
            group
        }!!

    private fun seedPendingPayment(group: OrderGroup): Payment =
        payments.save(Payment.prepare(group.id, group.publicId, PaymentMethod.CARD, group.finalPaymentAmount))

    private fun seedApprovedPaidGroup(buyerId: Long): Pair<OrderGroup, Payment> {
        val group = seedGroup(buyerId)
        val payment = payments.save(seedPendingPayment(group).approve("pg-tx-${group.publicId}", group.finalPaymentAmount))
        markGroup(group.publicId, "PAID")
        return group to payment
    }

    private fun markGroup(
        publicId: String,
        status: String,
    ) {
        tx.execute {
            em
                .createQuery(
                    "update OrderGroup g set g.status = com.aechak.domain.order.group.enums.OrderGroupStatus.$status where g.publicId = :pid",
                ).setParameter("pid", publicId)
                .executeUpdate()
        }
    }

    private fun pgRecord(
        key: String,
        status: PaymentGatewayStatus = PaymentGatewayStatus.PAID,
        paid: Long = 13_000L,
        cancelled: Long = 0L,
        changedAt: LocalDateTime = inWindow,
    ) = GatewayPaymentRecord(key, status, 13_000L, paid, cancelled, "pg-tx-$key", changedAt)

    private fun gatewayView(
        status: PaymentGatewayStatus = PaymentGatewayStatus.PAID,
        paid: Long = 13_000L,
    ) = PaymentGatewayView(
        status = status,
        totalAmount = 13_000L,
        paidAmount = paid,
        cancelledAmount = 0L,
        pgTxId = if (status == PaymentGatewayStatus.PAID) "stub-tx" else null,
        paidAt = if (status == PaymentGatewayStatus.PAID) LocalDateTime.of(2026, 10, 15, 14, 0) else null,
        failureCode = null,
    )

    private fun reconcile(date: LocalDate = baseDate) = useCase.reconcile(ReconcilePaymentsCommand(ReconciliationSource.PORTONE, date))

    private fun groupStatus(publicId: String): String =
        em
            .createQuery("select g.status from OrderGroup g where g.publicId = :pid", Any::class.java)
            .setParameter("pid", publicId)
            .singleResult
            .toString()

    private fun itemOf(
        runId: Long,
        key: String,
    ) = requireNotNull(items.findByRunIdAndPaymentKey(runId, key)) { "회차 $runId 에 $key 건이 기록돼 있어야 한다" }

    // ---------- 테스트 ----------

    @Test
    fun `PG는 승인인데 우리가 결제대기면 확정 본체를 태워 주문그룹과 결제를 결제완료로 만든다`() {
        val group = seedGroup(createActiveUser())
        seedPendingPayment(group)
        source.stub(pgRecord(group.publicId))
        gateway.stub(group.publicId, gatewayView())

        val result = reconcile()

        assertEquals(ReconcileOutcome.COMPLETED, result.outcome)
        assertEquals("PAID", groupStatus(group.publicId))
        assertEquals("APPROVED", payments.findByPaymentId(group.publicId)!!.status.name)
        val item = itemOf(result.runId!!, group.publicId)
        assertEquals(MismatchType.PG_PAID_OURS_UNCONFIRMED, item.mismatchType)
        assertEquals(ReconciliationResolution.AUTO_CONFIRMED, item.resolution)
        assertEquals(1, result.counts.autoResolved)
        assertEquals(1, result.counts.fetched)
    }

    @Test
    fun `양쪽이 같으면 일치로 기록하고 아무것도 바꾸지 않는다`() {
        val (group, _) = seedApprovedPaidGroup(createActiveUser())
        source.stub(pgRecord(group.publicId))

        val result = reconcile()

        val item = itemOf(result.runId!!, group.publicId)
        assertEquals(MismatchType.MATCHED, item.mismatchType)
        assertEquals(ReconciliationResolution.NOT_REQUIRED, item.resolution)
        assertEquals(1, result.counts.matched)
        assertTrue(gateway.cancelledPaymentIds.isEmpty())
    }

    @Test
    fun `취소된 주문그룹에 PG 승인이 있으면 환불 필요 건으로 사람 확인에 남긴다`() {
        val group = seedGroup(createActiveUser())
        seedPendingPayment(group)
        markGroup(group.publicId, "CANCELLED")
        source.stub(pgRecord(group.publicId))
        gateway.stub(group.publicId, gatewayView())

        val result = reconcile()

        val item = itemOf(result.runId!!, group.publicId)
        assertEquals(MismatchType.PG_PAID_GROUP_CANCELLED, item.mismatchType)
        assertEquals(ReconciliationResolution.MANUAL_REQUIRED, item.resolution)
        assertEquals("CANCELLED", groupStatus(group.publicId), "취소된 주문을 되살리지 않는다")
        assertEquals(1, result.counts.manualRequired)
    }

    @Test
    fun `자동 보정 중 3중 금액 대조에 걸리면 확정하지 않고 금액 불일치로 남긴다`() {
        val group = seedGroup(createActiveUser())
        seedPendingPayment(group)
        source.stub(pgRecord(group.publicId, paid = 12_000L))
        gateway.stub(group.publicId, gatewayView(paid = 12_000L))

        val result = reconcile()

        val item = itemOf(result.runId!!, group.publicId)
        assertEquals(MismatchType.AMOUNT_MISMATCH, item.mismatchType)
        assertEquals(ReconciliationResolution.MANUAL_REQUIRED, item.resolution)
        assertEquals("PENDING_PAYMENT", groupStatus(group.publicId), "금액이 다른 승인은 확정하지 않는다")
    }

    @Test
    fun `우리 원장에 없는 결제가 PG에 있으면 모르는 건으로 사람 확인이다`() {
        source.stub(pgRecord("01K0UNKNOWNUNKNOWNUNKNOWN01"))

        val result = reconcile()

        val item = itemOf(result.runId!!, "01K0UNKNOWNUNKNOWNUNKNOWN01")
        assertEquals(MismatchType.PG_ONLY_UNKNOWN, item.mismatchType)
        assertEquals(ReconciliationResolution.MANUAL_REQUIRED, item.resolution)
    }

    @Test
    fun `우리는 승인했는데 PG 목록에도 단건에도 없으면 가장 위험한 건으로 사람 확인이다`() {
        val (group, _) = seedApprovedPaidGroup(createActiveUser())
        // Fake 소스에 아무것도 심지 않음 — 목록은 비고 단건도 null. updated_at은 방금이라 창은 오늘을 포함해야 한다
        val result = reconcile(LocalDate.now())

        assertTrue(source.findCalledWith.contains(group.publicId), "PG 목록에 없던 우리 결제는 단건으로 다시 묻는다")
        val item = itemOf(result.runId!!, group.publicId)
        assertEquals(MismatchType.PG_NOT_PAID_OURS_APPROVED, item.mismatchType)
        assertEquals(ReconciliationResolution.MANUAL_REQUIRED, item.resolution)
    }

    @Test
    fun `같은 기준일을 다시 돌리면 건너뛰고 건은 늘지 않는다`() {
        val group = seedGroup(createActiveUser())
        seedPendingPayment(group)
        source.stub(pgRecord(group.publicId))
        gateway.stub(group.publicId, gatewayView())
        val first = reconcile()

        val second = reconcile()

        assertEquals(ReconcileOutcome.SKIPPED, second.outcome)
        assertEquals(1, items.findAllByRunId(first.runId!!).size)
        assertEquals(1, source.fetchWindows.size)
    }

    @Test
    fun `소스 장애로 실패한 회차는 다음 호출이 이어받아 완료한다`() {
        val group = seedGroup(createActiveUser())
        seedPendingPayment(group)
        source.stub(pgRecord(group.publicId))
        gateway.stub(group.publicId, gatewayView())
        source.failNextList()

        val failed = reconcile()
        assertEquals(ReconcileOutcome.FAILED, failed.outcome)
        assertEquals(ReconciliationRunStatus.FAILED, runs.findById(failed.runId!!)!!.status)

        val retried = reconcile()

        assertEquals(ReconcileOutcome.COMPLETED, retried.outcome)
        assertEquals(ReconciliationRunStatus.COMPLETED, runs.findById(failed.runId!!)!!.status)
        assertEquals(failed.runId, retried.runId, "같은 회차를 이어받는다")
        assertEquals(2, retried.attempt)
        assertEquals("PAID", groupStatus(group.publicId))
    }

    @Test
    fun `죽은 회차는 이어받고 살아 있는 회차는 건드리지 않는다`() {
        val window = LocalDateTime.of(2026, 10, 14, 0, 0) to LocalDateTime.of(2026, 10, 16, 0, 0)
        runs.save(
            PaymentReconciliationRun.start(
                ReconciliationSource.PORTONE,
                baseDate,
                window.first,
                window.second,
                LocalDateTime.now().minusHours(3),
            ),
        )
        val aliveDate = baseDate.minusDays(5)
        runs.save(PaymentReconciliationRun.start(ReconciliationSource.PORTONE, aliveDate, window.first, window.second, LocalDateTime.now()))

        val takenOver = reconcile(baseDate)
        val skipped = reconcile(aliveDate)

        assertEquals(ReconcileOutcome.COMPLETED, takenOver.outcome)
        assertEquals(2, takenOver.attempt)
        assertEquals(ReconcileOutcome.SKIPPED, skipped.outcome)
    }

    @Test
    fun `이전 회차의 실패 건은 다음 회차가 창과 무관하게 다시 판정하고 옛 건을 닫는다`() {
        val group = seedGroup(createActiveUser())
        seedPendingPayment(group)
        source.stub(pgRecord(group.publicId, changedAt = LocalDateTime.of(2026, 10, 14, 10, 0)))
        gateway.stub(group.publicId, gatewayView(status = PaymentGatewayStatus.READY, paid = 0L)) // 확정 본체의 재조회는 아직 미결제
        val first = reconcile(baseDate.minusDays(1))
        assertEquals(ReconciliationResolution.FAILED, itemOf(first.runId!!, group.publicId).resolution)

        gateway.stub(group.publicId, gatewayView()) // 이제 승인
        val second = reconcile(baseDate.plusDays(5)) // 창 밖의 날

        assertEquals(ReconciliationResolution.SUPERSEDED, itemOf(first.runId!!, group.publicId).resolution)
        assertEquals(ReconciliationResolution.AUTO_CONFIRMED, itemOf(second.runId!!, group.publicId).resolution)
        assertEquals("PAID", groupStatus(group.publicId))
    }

    @Test
    fun `회차가 보정 전에 죽으면 보정 대기 건이 다음 회차로 이월돼 확정된다`() {
        val group = seedGroup(createActiveUser())
        seedPendingPayment(group)
        val deadDate = baseDate.minusDays(3)
        val dead =
            runs.save(
                PaymentReconciliationRun.start(
                    ReconciliationSource.PORTONE,
                    deadDate,
                    deadDate.minusDays(1).atStartOfDay(),
                    deadDate.plusDays(1).atStartOfDay(),
                    LocalDateTime.now().minusHours(3),
                ),
            )
        items.save(
            com.aechak.domain.payment.reconciliation.PaymentReconciliationItem.compare(
                dead.id,
                ReconciliationSource.PORTONE,
                group.publicId,
                null,
                null,
                MismatchType.PG_PAID_OURS_UNCONFIRMED,
                ReconciliationResolution.AUTO_PENDING,
            ),
        )
        source.stub(pgRecord(group.publicId, changedAt = deadDate.atTime(9, 0))) // 이번 회차 창 밖
        gateway.stub(group.publicId, gatewayView())

        val result = reconcile()

        assertEquals(ReconciliationResolution.SUPERSEDED, itemOf(dead.id, group.publicId).resolution)
        assertEquals(ReconciliationResolution.AUTO_CONFIRMED, itemOf(result.runId!!, group.publicId).resolution)
        assertEquals("PAID", groupStatus(group.publicId))
    }

    @Test
    fun `승인 기록이 PG와 다르면 그룹이 결제대기여도 확정하지 않고 금액 불일치로 남긴다`() {
        val group = seedGroup(createActiveUser())
        payments.save(seedPendingPayment(group).approve("pg-tx-wrong", 12_000L)) // 승인 원장이 틀림, 그룹은 아직 결제대기
        source.stub(pgRecord(group.publicId))
        gateway.stub(group.publicId, gatewayView())

        val result = reconcile()

        val item = itemOf(result.runId!!, group.publicId)
        assertEquals(MismatchType.AMOUNT_MISMATCH, item.mismatchType)
        assertEquals(ReconciliationResolution.MANUAL_REQUIRED, item.resolution)
        assertEquals("PENDING_PAYMENT", groupStatus(group.publicId), "잘못된 승인 원장 위에 주문을 확정하지 않는다")
    }

    @Test
    fun `목록이 낡아 불일치로 보이면 단건으로 다시 확인해 일치로 남긴다`() {
        val (group, _) = seedApprovedPaidGroup(createActiveUser())
        source.stub(pgRecord(group.publicId, status = PaymentGatewayStatus.FAILED, paid = 0L)) // 목록 시점엔 실패
        source.stubSingle(pgRecord(group.publicId)) // 단건은 승인

        val result = reconcile()

        val item = itemOf(result.runId!!, group.publicId)
        assertEquals(MismatchType.MATCHED, item.mismatchType, "낡은 목록으로 '우리만 승인'을 남기면 안 된다")
        assertEquals(0, result.counts.manualRequired)
    }

    @Test
    fun `살아서 도는 다른 날짜 회차의 열린 건은 닫지 않는다`() {
        val aliveDate = baseDate.minusDays(1)
        val alive =
            runs.save(
                PaymentReconciliationRun.start(
                    ReconciliationSource.PORTONE,
                    aliveDate,
                    aliveDate.minusDays(1).atStartOfDay(),
                    aliveDate.plusDays(1).atStartOfDay(),
                    LocalDateTime.now(),
                ),
            )
        items.save(
            com.aechak.domain.payment.reconciliation.PaymentReconciliationItem.compare(
                alive.id,
                ReconciliationSource.PORTONE,
                "01K0ALIVEALIVEALIVEALIVE01",
                null,
                null,
                MismatchType.PG_ONLY_UNKNOWN,
                ReconciliationResolution.MANUAL_REQUIRED,
            ),
        )

        reconcile()

        assertEquals(ReconciliationResolution.MANUAL_REQUIRED, itemOf(alive.id, "01K0ALIVEALIVEALIVEALIVE01").resolution)
    }
}
