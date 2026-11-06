package com.aechak.application.support

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.dao.CannotAcquireLockException
import kotlin.test.assertEquals

/**
 * 계약: 교착으로 되돌아간 트랜잭션을 한 번만 다시 실행하는지.
 * 깨지면 저장과 뮤트 켜기가 끄기와 겹칠 때 500을 내거나 교착이 이어질 때 멈추지 않고 다시 실행한다.
 */
class DeadlockRetryTest {
    @Test
    fun `교착으로 한 번 실패하면 다시 실행한 결과를 돌려준다`() {
        var calls = 0

        val result = retryOnceOnDeadlock { if (++calls == 1) throw CannotAcquireLockException("deadlock") else "ok" }

        assertEquals("ok", result, "다시 실행한 결과여야 한다")
        assertEquals(2, calls, "한 번만 다시 실행해야 한다")
    }

    @Test
    fun `다시 실행해도 교착이면 그 예외를 던진다`() {
        var calls = 0

        assertThrows<CannotAcquireLockException> {
            retryOnceOnDeadlock {
                calls++
                throw CannotAcquireLockException("deadlock")
            }
        }

        assertEquals(2, calls, "두 번까지만 실행해야 한다")
    }
}
