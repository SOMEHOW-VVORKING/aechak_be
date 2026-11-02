package com.aechak.message.review

enum class SummaryScope {
    PRODUCT,
    SEGMENT,
}

enum class SummaryPolarity {
    PRO,
    CON,
}

data class SummaryQuoteSample(
    val reviewId: Long,
    val text: String,
)
