package com.aechak.domain.settlement

import com.aechak.common.error.BusinessException
import com.aechak.domain.settlement.batch.SettlementExecutionBatch
import com.aechak.domain.settlement.batch.enums.SettlementBatchStatus
import com.aechak.domain.settlement.error.SettlementErrorCode
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/**
 * 집행 배치 상태머신 계약. 깨지면 끝나지 않은 배치가 완료로 보여 재실행이 지급을 건너뛴다.
 */
class SettlementExecutionBatchTest {
    @Test
    fun `편성된 배치는 진행중을 거쳐 완료로 간다`() {
        val batch = batch()

        batch.startProcessing()
        assertEquals(SettlementBatchStatus.PROCESSING, batch.status)

        batch.complete()
        assertEquals(SettlementBatchStatus.COMPLETED, batch.status)
    }

    @Test
    fun `진행중을 건너뛴 완료는 거절된다`() {
        val batch = batch()

        val exception = assertFailsWith<BusinessException> { batch.complete() }

        assertEquals(SettlementErrorCode.INVALID_SETTLEMENT_BATCH_STATUS_TRANSITION, exception.errorCode)
        assertEquals(SettlementBatchStatus.GENERATED, batch.status)
    }

    @Test
    fun `완료된 배치는 다시 진행중이 되지 않는다`() {
        val batch = batch().apply { startProcessing() }.apply { complete() }

        assertFailsWith<BusinessException> { batch.startProcessing() }
        assertEquals(SettlementBatchStatus.COMPLETED, batch.status)
    }

    private fun batch(): SettlementExecutionBatch = SettlementExecutionBatch.generate(LocalDate.of(2026, 3, 20), 3, 120_000L)
}
