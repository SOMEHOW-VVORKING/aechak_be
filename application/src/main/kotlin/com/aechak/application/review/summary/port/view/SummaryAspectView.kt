package com.aechak.application.review.summary.port.view

import com.aechak.domain.review.summary.enums.AspectPolarity

data class SummaryAspectView(
    val reviewId: Long,
    val segmentKey: String?,
    val aspectCode: String,
    val polarity: AspectPolarity,
    val label: String,
    val quote: String,
)
