package com.aechak.application.product.stats.usecase.command

import java.math.BigDecimal

/** 리뷰에서 다시 센 평점 집계 절대값. */
data class ApplyReviewStatsCommand(
    val productId: Long,
    val reviewCount: Int,
    val ratingSum: Long,
    val averageRating: BigDecimal?,
)
