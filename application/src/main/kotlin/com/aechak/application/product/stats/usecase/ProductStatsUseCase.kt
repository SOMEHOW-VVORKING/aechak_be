package com.aechak.application.product.stats.usecase

import com.aechak.application.product.stats.usecase.command.ApplyReviewStatsCommand

interface ProductStatsUseCase {
    fun applyReviewStats(command: ApplyReviewStatsCommand)
}
