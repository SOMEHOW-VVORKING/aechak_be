package com.aechak.domain.support

import java.time.Clock
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneOffset
import kotlin.test.Test
import kotlin.test.assertEquals

class DbTimeTest {
    @Test
    fun `나노초는 반올림하지 않고 마이크로초로 버린다`() {
        val clock = Clock.fixed(Instant.parse("2026-11-07T00:00:00.123456789Z"), ZoneOffset.UTC)

        assertEquals(LocalDateTime.parse("2026-11-07T00:00:00.123456"), dbNow(clock))
    }
}
