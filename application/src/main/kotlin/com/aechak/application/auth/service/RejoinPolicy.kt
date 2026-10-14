package com.aechak.application.auth.service

import java.time.Duration
import java.time.LocalDateTime

/** 탈퇴 후 재가입을 제한할 기간 */
data class RejoinPolicy(
    val blockedPeriod: Duration,
) {
    /**
     * 제한이 끝나는 날의 자정부터 재가입을 연다.
     * 시각 단위로 끊으면 안내한 날짜와 실제로 열리는 시점이 어긋난다.
     */
    fun allowedFrom(withdrawnAt: LocalDateTime): LocalDateTime =
        withdrawnAt
            .toLocalDate()
            .plusDays(blockedPeriod.toDays())
            .atStartOfDay()
}
