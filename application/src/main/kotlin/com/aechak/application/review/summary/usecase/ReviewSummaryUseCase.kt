package com.aechak.application.review.summary.usecase

import com.aechak.application.review.summary.usecase.command.ApplyReviewSummaryResultCommand
import com.aechak.application.review.summary.usecase.result.ProductReviewSummaryResult

interface ReviewSummaryUseCase {
    /** 추출 결과는 늘 저장함. 요약은 상품의 가장 최근 요청에 대한 응답일 때만 저장하고 그때 true */
    fun applyResult(command: ApplyReviewSummaryResultCommand): Boolean

    /** 상품 전체 요약과, 조회자 대표 펫 체급의 요약. 비로그인이거나 펫이 없으면 체급 요약은 null */
    fun getProductReviewSummary(
        productPublicId: String,
        viewerUserId: Long?,
    ): ProductReviewSummaryResult
}
