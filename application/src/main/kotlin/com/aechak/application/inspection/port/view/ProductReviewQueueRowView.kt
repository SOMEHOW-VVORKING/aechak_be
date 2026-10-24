package com.aechak.application.inspection.port.view

import com.aechak.domain.inspection.enums.InspectionJudgment
import com.aechak.domain.inspection.enums.ReviewQueueStatus
import java.time.LocalDateTime

data class ProductReviewQueueRowView(
    val productPublicId: String,
    val productName: String,
    val sellerName: String,
    val status: ReviewQueueStatus,
    /** 상품의 가장 최근 검수 판정. 셀러가 고친 내용을 AI가 아직 보는 중이면 null */
    val latestJudgment: InspectionJudgment?,
    val blindedAt: LocalDateTime,
)
