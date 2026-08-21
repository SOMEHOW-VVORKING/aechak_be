package com.aechak.application.auth.service

import java.time.Duration

/** 탈퇴 후 재가입을 제한할 기간 */
data class RejoinPolicy(
    val blockedPeriod: Duration,
)
