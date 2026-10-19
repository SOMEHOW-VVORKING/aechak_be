package com.aechak.application.product.draft.usecase.result

import com.aechak.domain.product.draft.ProductDraft

/** 초안은 셀러당 상한이 걸려 있어 페이지네이션 없이 전량 내린다 */
data class ProductDraftListResult(
    val items: List<ProductDraftSummaryResult>,
    val totalCount: Int,
) {
    companion object {
        fun from(drafts: List<ProductDraft>): ProductDraftListResult =
            ProductDraftListResult(
                items = drafts.map(ProductDraftSummaryResult::from),
                totalCount = drafts.size,
            )
    }
}
