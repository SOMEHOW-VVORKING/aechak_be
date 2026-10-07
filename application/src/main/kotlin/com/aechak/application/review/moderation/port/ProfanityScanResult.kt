package com.aechak.application.review.moderation.port

/** 금칙어 스캔 결과. */
data class ProfanityScanResult(
    val hasMatch: Boolean,
    val maskedContent: String,
    val matchedRatio: Double,
)
