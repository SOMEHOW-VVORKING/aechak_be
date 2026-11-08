package com.aechak.application.file.support

import com.aechak.application.ratelimit.usecase.RateLimit
import com.aechak.application.ratelimit.usecase.RateLimitRule
import java.time.Duration

/** 파일 행동별 빈도 제한 */
enum class FileRateLimitRule(
    vararg limits: RateLimit,
) : RateLimitRule {
    // 용도와 상관없이 함께 센다
    ISSUE_UPLOAD_URL(RateLimit(60, Duration.ofMinutes(1)), RateLimit(600, Duration.ofHours(1))),
    ;

    override val key: String get() = "file:$name"
    override val limits: List<RateLimit> = limits.toList()
}
