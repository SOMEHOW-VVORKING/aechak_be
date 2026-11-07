package com.aechak.domain.support

import java.time.Clock
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit

/** 저장할 현재 시각. DATETIME(6)은 나노초를 반올림해 저장하므로 미리 버려 메모리 값과 DB 값을 같게 한다 */
fun dbNow(clock: Clock = Clock.systemDefaultZone()): LocalDateTime = LocalDateTime.now(clock).truncatedTo(ChronoUnit.MICROS)
