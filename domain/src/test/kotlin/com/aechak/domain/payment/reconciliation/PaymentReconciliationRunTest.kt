package com.aechak.domain.payment.reconciliation

import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** 계약 테스트. 깨지면 살아 있는 회차를 다른 실행이 가로채거나, 죽은 회차를 아무도 이어받지 못한다. */
class PaymentReconciliationRunTest {
    private val now = LocalDateTime.of(2026, 10, 16, 4, 30)
    private val staleAfter = Duration.ofHours(2)

    private fun running(startedAt: LocalDateTime = now) =
        PaymentReconciliationRun.start(
            source = ReconciliationSource.PORTONE,
            baseDate = LocalDate.of(2026, 10, 15),
            windowFrom = LocalDateTime.of(2026, 10, 14, 0, 0),
            windowUntil = LocalDateTime.of(2026, 10, 16, 0, 0),
            now = startedAt,
        )

    @Test
    fun `새 회차는 RUNNING이고 첫 시도다`() {
        val run = running()

        assertEquals(ReconciliationRunStatus.RUNNING, run.status)
        assertEquals(1, run.attempt)
        assertTrue(run.isOwnedBy(1), "막 연 회차는 첫 시도의 소유다")
        assertFalse(run.canBeTakenOver(now, staleAfter), "살아 있는 회차를 바로 가로채면 두 실행이 같은 창을 동시에 돈다")
    }

    @Test
    fun `창의 시작이 끝보다 늦으면 열 수 없다`() {
        assertFailsWith<IllegalArgumentException> {
            PaymentReconciliationRun.start(
                ReconciliationSource.PORTONE,
                LocalDate.of(2026, 10, 15),
                windowFrom = LocalDateTime.of(2026, 10, 16, 0, 0),
                windowUntil = LocalDateTime.of(2026, 10, 14, 0, 0),
                now = now,
            )
        }
    }

    @Test
    fun `기한을 넘긴 RUNNING은 죽은 회차라 이어받을 수 있다`() {
        val stale = running(startedAt = now.minusHours(3))

        assertTrue(stale.isStale(now, staleAfter))
        assertTrue(stale.canBeTakenOver(now, staleAfter))
        assertFalse(running(startedAt = now.minusMinutes(90)).canBeTakenOver(now, staleAfter), "기한 안의 RUNNING은 아직 살아 있는 것으로 본다")
    }

    @Test
    fun `실패한 회차는 이어받을 수 있고 완료된 회차는 손대지 않는다`() {
        val failed = running().fail(ReconciliationCounts.ZERO, "source down", now)
        val completed = running().complete(ReconciliationCounts(3, 3, 3, 0, 0, 0), now)

        assertTrue(failed.canBeTakenOver(now, staleAfter))
        assertFalse(completed.canBeTakenOver(now, staleAfter), "완료된 회차를 다시 열면 같은 날을 두 번 처리한다")
        assertFalse(completed.isOwnedBy(1), "완료된 회차에는 더 이상 쓸 수 없다")
    }

    @Test
    fun `이어받으면 시도 횟수가 오르고 이전 세대의 소유권이 사라진다`() {
        val resumed = running(startedAt = now.minusHours(3)).resume(now)

        assertEquals(2, resumed.attempt)
        assertEquals(ReconciliationRunStatus.RUNNING, resumed.status)
        assertEquals(now, resumed.startedAt)
        assertNull(resumed.finishedAt)
        assertTrue(resumed.isOwnedBy(2))
        assertFalse(resumed.isOwnedBy(1), "이전 실행이 늦게 깨어나도 자기 세대로는 쓸 수 없어야 한다")
    }

    @Test
    fun `실패 메시지는 컬럼 길이에 맞춰 잘린다`() {
        val failed = running().fail(ReconciliationCounts.ZERO, "x".repeat(5_000), now)

        assertEquals(PaymentReconciliationRun.ERROR_MESSAGE_MAX_LENGTH, failed.errorMessage?.length)
    }

    @Test
    fun `사람 확인이나 실패가 하나라도 있으면 주의가 필요하다`() {
        assertTrue(ReconciliationCounts(10, 10, 9, 0, 1, 0).needsAttention)
        assertTrue(ReconciliationCounts(10, 10, 9, 0, 0, 1).needsAttention)
        assertFalse(ReconciliationCounts(10, 10, 8, 2, 0, 0).needsAttention, "자동 보정으로 끝난 건은 사람을 부르지 않는다")
    }
}
